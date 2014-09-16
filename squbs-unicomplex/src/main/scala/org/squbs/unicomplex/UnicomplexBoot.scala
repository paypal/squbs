/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.unicomplex

import java.io.{FileInputStream, InputStreamReader, Reader, File}
import java.util.concurrent.TimeUnit
import java.util.{TimerTask, Timer}
import java.util.jar.JarFile

import akka.actor._
import akka.routing.FromConfig
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config._
import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.util.{Try, Success, Failure}

import org.slf4j.LoggerFactory
import org.squbs.lifecycle.ExtensionLifecycle

import ConfigUtil._
import UnicomplexBoot.Cube

object UnicomplexBoot {

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  final val extConfigDirKey = "squbs.external-config-dir"
  final val extConfigNameKey = "squbs.external-config-files"
  final val actorSystemNameKey = "squbs.actorsystem-name"

  val startupTimeout: Timeout =
    Try(System.getProperty("startup.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)

  object StartupType extends Enumeration {
    type StartupType = Value
    val
    // Identifies extensions
    EXTENSIONS,

    // Identifies actors as startup type
    ACTORS,

    // Identifies service as startup type
    SERVICES = Value
  }

  case class Cube(jarPath: String, symName: String, alias: String, version: String,
                  components: Map[StartupType.Value, Seq[Config]])

  val actorSystems = TrieMap.empty[String, ActorSystem]

  def apply(addOnConfig: Config): UnicomplexBoot = {
    val startTime = Timestamp(System.nanoTime, System.currentTimeMillis)
    UnicomplexBoot(startTime, Option(addOnConfig), getFullConfig(Option(addOnConfig)))
  }

  def apply(actorSystemCreator : (String, Config) => ActorSystem): UnicomplexBoot = {
    val startTime = Timestamp(System.nanoTime, System.currentTimeMillis)
    UnicomplexBoot(startTime, None, getFullConfig(None), actorSystemCreator)
  }

  def getFullConfig(addOnConfig: Option[Config]): Config = {
    val baseConfig = ConfigFactory.load()
    // 1. See whether add-on config is there.
    addOnConfig match {
      case Some(config) =>
        ConfigFactory.load(config withFallback baseConfig)
      case None =>
        // Sorry, the configDir is used to read the file. So it cannot be read from this config file.
        val configDir = new File(baseConfig.getString(extConfigDirKey))
        import collection.JavaConversions._
        var configNames = baseConfig.getStringList(extConfigNameKey)
        configNames.add("application")
        val parseOptions = ConfigParseOptions.defaults().setAllowMissing(true)
        val addConfigs = configNames map {
        	name => ConfigFactory.parseFileAnySyntax(new File(configDir, name), parseOptions)
        }
        if (addConfigs.isEmpty) baseConfig
        else ConfigFactory.load((addConfigs :\ baseConfig) (_ withFallback _))
    }
  }

  private[unicomplex] def scan(jarNames: Seq[String])(boot: UnicomplexBoot):
        UnicomplexBoot = {
    val configEntries = jarNames map readConfigs

    val jarConfigs = jarNames zip configEntries collect { case (jar, Some(cfg)) => (jar, cfg) }

    val cubeList = resolveAliasConflicts(jarConfigs map { case (jar, config) => readCube(jar, config) } collect {
      case Some(cube) => cube
    })

    // Read listener and alias information.
    val (activeAliases, activeListeners, missingAliases) = findListeners(boot.config, cubeList)
    missingAliases foreach { name => log.warn(s"Requested listener $name not found!") }
    boot.copy(cubes = cubeList, jarConfigs = jarConfigs, jarNames = jarNames,
      listeners = activeListeners, listenerAliases = activeAliases)
  }

  private[this] def readConfigs(jarName: String): Option[Config] = {

    // Make it extra lazy, so that we do not create the next File if the previous one succeeds.
    val configExtensions = Stream("conf", "json", "properties")

    val jarFile = new File(jarName)

    var fileName: String = "" // Contains the evaluated config file name, used for reporting errors.
    var configReader: Option[Reader] = None

    try {
      configReader =
        if (jarFile.isDirectory) {

          def getConfFile(ext: String) = {
            fileName = "META-INF/squbs-meta." + ext
            val confFile = new File(jarFile, fileName)
            if (confFile.isFile) Option(new InputStreamReader(new FileInputStream(confFile), "UTF-8"))
            else None
          }
          (configExtensions map getConfFile find { _ != None }).flatten

        } else if (jarFile.isFile) {

          val jar = new JarFile(jarFile)

          def getConfFile(ext: String) = {
            fileName = "META-INF/squbs-meta." + ext
            val confFile = jar.getEntry(fileName)
            if (confFile != null && !confFile.isDirectory)
              Option(new InputStreamReader(jar.getInputStream(confFile), "UTF-8"))
            else None
          }
          (configExtensions map getConfFile find { _ != None }).flatten
        } else None

      configReader map ConfigFactory.parseReader

    } catch {
      case e: Exception =>
        log.info(s"${e.getClass.getName} reading configuration from $jarName : $fileName.\n ${e.getMessage}")
        None
    } finally {
      configReader match {
        case Some(reader) => reader.close()
        case None =>
      }
    }
  }

  private[this] def readCube(jarPath: String, config: Config): Option[Cube] = {
    val cubeName =
      try {
        config.getString("cube-name")
      } catch {
        case e: ConfigException => return None
      }

    val cubeVersion =
      try {
        config.getString("cube-version")
      } catch {
        case e: ConfigException => return None
      }

    val cubeAlias = cubeName.substring(cubeName.lastIndexOf('.') + 1)


    val c =
      Seq(config.getOptionalConfigList("squbs-actors") map ((StartupType.ACTORS, _)),
        config.getOptionalConfigList("squbs-services") map ((StartupType.SERVICES, _)),
        config.getOptionalConfigList("squbs-extensions") map ((StartupType.EXTENSIONS, _)))
      .collect { case Some((sType, configs)) => (sType, configs) } .toMap

    Some(Cube(jarPath, cubeName, cubeAlias, cubeVersion, c))
  }

  // Resolve cube alias conflict by making it longer on demand.
  @tailrec
  private[unicomplex] def resolveAliasConflicts(cubeList: Seq[Cube]): Seq[Cube] = {

    val aliasConflicts = cubeList map { cube =>
      (cube.alias, cube.symName)
    } groupBy (_._1) mapValues { seq =>
      (seq map (_._2)).toSet
    } filter { _._2.size > 1 }

    if (aliasConflicts.isEmpty) cubeList
    else {

      var updated = false

      val newAliases = (aliasConflicts map { case (alias, conflicts) =>
        conflicts.toSeq map { symName =>
          val idx = symName.lastIndexOf('.', symName.length - alias.length - 2)
          if (idx > 0) {
            updated = true
            (symName, symName.substring(idx + 1))
          }
          else (symName, symName)
        }
      }).flatten.toSeq

      if (updated) {
        val updatedList = cubeList map { cube =>
          newAliases find { case (symName, alias) => symName == cube.symName } match {
            case Some((symName, alias)) => cube.copy(alias = alias)
            case None => cube
          }
        }
        resolveAliasConflicts(updatedList)
      }
      else sys.error("Duplicate cube names: " + (aliasConflicts flatMap (_._2) mkString ", "))
    }
  }

  private [unicomplex] def startComponents(cube: Cube, aliases: Map[String, String])
                                          (implicit actorSystem: ActorSystem) = {
    import cube.{jarPath, symName, alias, version, components}
    val cubeSupervisor = actorSystem.actorOf(Props[CubeSupervisor], alias)
    Unicomplex(actorSystem).uniActor ! CubeRegistration(alias, symName, version, cubeSupervisor)

    def startActor(actorConfig: Config): Option[(String, String, String, Class[_])] = {
      val className = actorConfig getString "class-name"
      val name = actorConfig getOptionalString "name" getOrElse (className substring (className.lastIndexOf('.') + 1))
      val withRouter = actorConfig getOptionalBoolean "with-router" getOrElse false
      val initRequired = actorConfig getOptionalBoolean "init-required" getOrElse false

      try {
        val clazz = Class.forName(className, true, getClass.getClassLoader)
        val actorClass = clazz asSubclass classOf[Actor]

        // Create and the props for this actor to be started, optionally enabling the router.
        val props = if (withRouter) Props(actorClass) withRouter FromConfig() else Props(actorClass)

        // Send the props to be started by the cube.
        cubeSupervisor ! StartCubeActor(props, name, initRequired)
        Some((symName, alias, version, clazz))
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          log.warn(s"Can't load actor: $className.\n" +
            s"Cube: $symName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          None
      }
    }

    def startServiceRoute(clazz: Class[_], webContext: String, listeners: Seq[String]) = {
      try {
        val routeClass = clazz asSubclass classOf[RouteDefinition]
        val props = Props(classOf[RouteActor], webContext, routeClass)
        val className = clazz.getSimpleName
        val actorName = if (webContext.length > 0) s"$webContext-$className-route" else s"root-$className-route"
        cubeSupervisor ! StartCubeService(webContext, listeners, props, actorName, initRequired = true)
        Some((symName, alias, version, clazz))
      } catch {
        case e: ClassCastException => None
      }
    }

    def startServiceActor(clazz: Class[_], webContext: String, listeners: Seq[String],
                          initRequired: Boolean) = {
      try {
        val actorClass = clazz asSubclass classOf[Actor]
        val props = Props { WebContext.createWithContext(webContext){ actorClass.newInstance() } }
        val className = clazz.getSimpleName
        val actorName = if (webContext.length > 0) s"$webContext-$className-handler" else s"root-$className-handler"
        cubeSupervisor ! StartCubeService(webContext, listeners, props, actorName, initRequired)
        Some((symName, alias, version, actorClass))
      } catch {
        case e: ClassCastException => None
      }
    }

    def startService(serviceConfig: Config): Option[(String, String, String, Class[_])] =
    try {
      val className = serviceConfig.getString("class-name")
      val clazz = Class.forName(serviceConfig.getString("class-name"), true, getClass.getClassLoader)

      val webContext = serviceConfig.getString("web-context")

      val listeners = serviceConfig.getOptionalStringList("listeners").fold(Seq("default-listener"))({ list =>

        val listenerMapping = list collect {
          case entry if entry != "*" => (entry, aliases get entry)
        }

        listenerMapping foreach {
          // Make sure we report any missing listeners
          case (entry, None) =>
            log.warn(s"Listener $entry required by $symName is not configured. Ignoring.")
          case _ =>
        }

        if (list contains "*") aliases.values.toSeq.distinct
        else listenerMapping collect { case (entry, Some(listener)) => listener }
      })

      val service = startServiceRoute(clazz, webContext, listeners) orElse startServiceActor(
        clazz, webContext, listeners, serviceConfig getOptionalBoolean "init-required" getOrElse false)

      if (service == None) throw new ClassCastException(s"Class $className is neither a RouteDefinition nor an Actor.")
      service

    } catch {
        case e: Exception =>
          val t = getRootCause(e)
          log.warn(s"Can't load service definition $serviceConfig.\n" +
            s"Cube: $symName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          None
      }

    val actorConfigs = components.getOrElse(StartupType.ACTORS, Seq.empty)
    val routeConfigs = components.getOrElse(StartupType.SERVICES, Seq.empty)

    val actorInfo = actorConfigs map startActor
    val routeInfo = routeConfigs map startService

    cubeSupervisor ! Started // Tell the cube all actors to be started are started.
    log.info(s"Started cube $symName $version")
    (actorInfo ++ routeInfo) collect { case Some(component) => component }
  }

  def configuredListeners(config: Config): Map[String, Config] = {
    import collection.JavaConversions._
    val listeners = config.root.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.getOptionalString("type") == Some("squbs.listener") => (n, v.toConfig)
    }
    // Check for duplicates
    val listenerMap = mutable.Map.empty[String, Config]
    listeners foreach { case (name, cfg) =>
        listenerMap.get(name) match {
          case Some(_) => log.warn(s"Duplicate listener $name already declared. Ignoring.")
          case None => listenerMap += name -> cfg
        }
    }
    listenerMap.toMap
  }

  def findListenerAliases(listeners: Map[String, Config]): Map[String, String] = {
    val aliases =
    for ((name, config) <- listeners) yield {
      val aliasNames = config.getOptionalStringList("aliases") getOrElse Seq.empty[String]
      (name, name) +: (aliasNames map ((_, name)))
    }
    val aliasMap = mutable.Map.empty[String, String]

    // Check for duplicate aliases
    for {
      group <- aliases
      (alias, listener) <- group
    } {
      aliasMap.get(alias) match {
        case Some(l) =>
          log.warn(s"Duplicate alias $alias for listener $listener already declared for listener $l. Ignoring.")
        case None => aliasMap += alias -> listener
      }
    }
    aliasMap.toMap
  }

  def findListeners(config: Config, cubes: Seq[Cube]) = {
    val demandedListeners =
    for {
      routes <- cubes.map { _.components.get(StartupType.SERVICES) } .collect { case Some(routes) => routes } .flatten
      routeListeners <- routes getOptionalStringList "listeners" getOrElse Seq("default-listener")
      if routeListeners != "*" // Filter out wildcard listener bindings, not starting those.
    } yield {
      routeListeners
    }
    val listeners = configuredListeners(config)
    val aliases = findListenerAliases(listeners)
    val activeAliases = aliases filter { case (n, _) => demandedListeners contains n }
    val missingAliases = demandedListeners filterNot { l => activeAliases exists { case (n, _) => n == l } }
    val activeListenerNames = activeAliases.values
    val activeListeners = listeners filter { case (n, c) => activeListenerNames exists (_ == n) }
    (activeAliases, activeListeners, missingAliases)
  }

  def startServiceInfra(boot: UnicomplexBoot)(implicit actorSystem: ActorSystem) {
    import actorSystem.dispatcher
    val startTime = System.nanoTime
    implicit val timeout = Timeout((boot.listeners.size * 5) seconds)
    val ackFutures =
      for ((listenerName, config) <- boot.listeners) yield {
        Unicomplex(actorSystem).uniActor ? StartListener(listenerName, config)
      }
    // Block for the web service to be started.
    Await.ready(Future.sequence(ackFutures), timeout.duration)

    val elapsed = (System.nanoTime - startTime) / 1000000
    log.info(s"Web Service started in $elapsed milliseconds")
  }

  private[unicomplex] def getRootCause(e: Exception) = {
    var t : Throwable = e
    var cause = e.getCause
    while (cause != null) {
      t = cause
      cause = t.getCause
    }
    t
  }
}

case class UnicomplexBoot private[unicomplex] (startTime: Timestamp,
                          addOnConfig: Option[Config] = None,
                          config: Config,
                          actorSystemCreator: (String, Config) => ActorSystem =
                            {(name, config) => ActorSystem(name, config)},
                          cubes: Seq[Cube] = Seq.empty,
                          listeners: Map[String, Config] = Map.empty,
                          listenerAliases: Map[String, String] = Map.empty,
                          jarConfigs: Seq[(String, Config)] = Seq.empty,
                          jarNames: Seq[String] = Seq.empty,
                          actors: Seq[(String, String, String, Class[_])] = Seq.empty,
                          extensions: Seq[(String, String, ExtensionLifecycle)] = Seq.empty,
                          started: Boolean = false,
                          stopJVM: Boolean = false) {

  import UnicomplexBoot._

  def actorSystemName = config.getString(actorSystemNameKey)

  def actorSystem = UnicomplexBoot.actorSystems(actorSystemName)

  def externalConfigDir = config.getString(extConfigDirKey)

  def createUsing(actorSystemCreator: (String, Config) => ActorSystem) = copy(actorSystemCreator = actorSystemCreator)

  def scanComponents(jarNames: Seq[String]): UnicomplexBoot = scan(jarNames)(this)

  def initExtensions: UnicomplexBoot = {

    val initSeq = cubes.flatMap { cube =>
      cube.components.getOrElse(StartupType.EXTENSIONS, Seq.empty) map { config =>
        val className = config getString "class-name"
        val seqNo = config getOptionalInt "sequence" getOrElse Int.MaxValue
        (seqNo, className, cube.symName, cube.version, cube.jarPath)
      }
    } .sortBy (_._1)

    // preInit extensions
    val extensions = initSeq map (preInitExtension _).tupled collect { case Some(extension) => extension }

    // Init extensions
    extensions foreach {
      case (symName, version, extLifecycle) =>
        extLifecycle.init()
        log.info(s"Started extension ${extLifecycle.getClass.getName} in $symName $version")
    }

    copy(extensions = extensions)
  }

  def stopJVMOnExit: UnicomplexBoot = copy(stopJVM = true)

  def start(): UnicomplexBoot = synchronized {

    if (started) throw new IllegalStateException("Unicomplex already started!")

    // Extensions may have changed the config. So we need to reload the config here.
    val newConfig = UnicomplexBoot.getFullConfig(addOnConfig)
    val newName = config.getString(UnicomplexBoot.actorSystemNameKey)

    implicit val actorSystem = {
      val system = actorSystemCreator(newName, newConfig)
      system.registerExtension(Unicomplex)
      Unicomplex(system).setScannedComponents(jarNames)
      system
    }

    UnicomplexBoot.actorSystems += actorSystem.name -> actorSystem
    actorSystem.registerOnTermination { UnicomplexBoot.actorSystems -= actorSystem.name }

    registerExtensionShutdown(actorSystem)

    val uniActor = Unicomplex(actorSystem).uniActor

    // Send start time to Unicomplex
    uniActor ! startTime

    // Register extensions in Unicomplex actor
    uniActor ! Extensions(extensions)

    val startServices = listeners.nonEmpty && cubes.exists(_.components.contains(StartupType.SERVICES))

    // Notify Unicomplex that services will be started.
    if (startServices) uniActor ! PreStartWebService(listeners)

    // Signal started to Unicomplex.
    uniActor ! Started

    // Start all actors
    val actors = cubes.map(startComponents(_, listenerAliases)).flatten

    // Start the service infrastructure if services are enabled and registered.
    if (startServices) startServiceInfra(this)

    extensions foreach { case (jarName, jarVersion, extLifecycle) => extLifecycle.postInit() }

    {
      // Tell Unicomplex we're done.
      implicit val timeout = startupTimeout
      val stateFuture = Unicomplex(actorSystem).uniActor ? Activate
      Try(Await.result(stateFuture, timeout.duration)) match {
        case Success(Active) => log.info(s"[$actorSystemName] activated")
        case _ => log.warn(s"[$actorSystemName] initialization failed.")
      }
    }

    copy(config = actorSystem.settings.config, actors = actors, extensions = extensions, started = true)
  }

  def registerExtensionShutdown(actorSystem: ActorSystem) {
    if (extensions.nonEmpty) {
      actorSystem.registerOnTermination {
        // Run the shutdown in a different thread, not in the ActorSystem's onTermination thread.
        import scala.concurrent.Future

        // Kill the JVM if the shutdown takes longer than the timeout.
        if (stopJVM) {
          val shutdownTimer = new Timer(true)
          shutdownTimer.schedule(new TimerTask {
            def run() {
              System.exit(0)
            }
          }, 5000)
        }

        // Then run the shutdown in the global execution context.
        import scala.concurrent.ExecutionContext.Implicits.global
        Future {
          extensions.reverse foreach { case (symName, version, extLifecycle) =>
            extLifecycle.shutdown()
            log.info(s"Shutting down extension ${extLifecycle.getClass.getName} in $symName $version")
          }
        } onComplete {
          case Success(result) =>
            log.info(s"ActorSystem ${actorSystem.name} shutdown complete")
            if (stopJVM) System.exit(0)

          case Failure(e) =>
            log.error(s"Error occurred during shutdown extensions: $e")
            if (stopJVM) System.exit(-1)
        }
      }
    }
  }

  def preInitExtension(seqNo: Int, className: String, symName: String, version: String, jarPath: String):
                      Option[(String, String, ExtensionLifecycle)] = {
    try {
      val clazz = Class.forName(className, true, getClass.getClassLoader)
      val extLifecycle = ExtensionLifecycle(this) { clazz.asSubclass(classOf[ExtensionLifecycle]).newInstance }
      extLifecycle.preInit()
      Some((symName, version, extLifecycle))
    } catch {
      case e: Exception =>
        val t = getRootCause(e)
        log.warn(s"Can't load extension $className.\n" +
          s"Cube: $symName $version\n" +
          s"Path: $jarPath\n" +
          s"${t.getClass.getName}: ${t.getMessage}")
        t.printStackTrace()
        None
    }
  }
}

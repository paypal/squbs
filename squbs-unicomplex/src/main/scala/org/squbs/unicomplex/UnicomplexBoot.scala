/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.squbs.unicomplex

import java.io.{File, FileInputStream, InputStreamReader, Reader}
import java.net.URL
import java.util.concurrent.{TimeoutException, TimeUnit}
import java.util.jar.JarFile
import java.util.{Timer, TimerTask}

import akka.actor._
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import com.typesafe.config._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.lifecycle.ExtensionLifecycle
import org.squbs.unicomplex.ConfigUtil._
import org.squbs.unicomplex.UnicomplexBoot.CubeInit

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object UnicomplexBoot extends LazyLogging {

  final val extConfigDirKey = "squbs.external-config-dir"
  final val extConfigNameKey = "squbs.external-config-files"
  final val actorSystemNameKey = "squbs.actorsystem-name"

  val defaultStartupTimeout: Timeout =
    Try(System.getProperty("startup.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (1 minute)

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

  case class CubeInit(info: Cube, components: Map[StartupType.Value, Seq[Config]])

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
        val configNames = baseConfig.getStringList(extConfigNameKey)
        configNames.add("application")
        val parseOptions = ConfigParseOptions.defaults().setAllowMissing(true)
        val addConfigs = configNames map {
          name => ConfigFactory.parseFileAnySyntax(new File(configDir, name), parseOptions)
        }
        if (addConfigs.isEmpty) baseConfig
        else ConfigFactory.load((addConfigs :\ baseConfig) (_ withFallback _))
    }
  }

  private[unicomplex] def scan(jarNames: Seq[String])(boot: UnicomplexBoot): UnicomplexBoot = {
    val configEntries = jarNames map readConfigs
    val jarConfigs = jarNames zip configEntries collect { case (jar, Some(cfg)) => (jar, cfg) }
    resolveCubes(jarConfigs, boot.copy(jarNames = jarNames))
  }

  private[unicomplex] def scanResources(resources: Seq[URL], withClassPath: Boolean = true)(boot: UnicomplexBoot):
      UnicomplexBoot = {
    val cpResources: Seq[URL] =
      if (withClassPath) {
        val loader = getClass.getClassLoader
        import scala.collection.JavaConversions._
        Seq("conf", "json", "properties") flatMap { ext => loader.getResources(s"META-INF/squbs-meta.$ext") }
      } else Seq.empty

    val jarConfigs = (cpResources ++ resources) map readConfigs collect { case Some(jarCfg) => jarCfg }
    resolveCubes(jarConfigs, boot)
  }

  private[this] def resolveCubes(jarConfigs: Seq[(String, Config)], boot: UnicomplexBoot) = {

    val cubeList = resolveAliasConflicts(jarConfigs map { case (jar, config) => readCube(jar, config) } collect {
      case Some(cube) => cube
    })

    // Read listener and alias information.
    val (activeAliases, activeListeners, missingAliases) = findListeners(boot.config, cubeList)
    missingAliases foreach { name => logger.warn(s"Requested listener $name not found!") }
    boot.copy(cubes = cubeList, jarConfigs = jarConfigs, listeners = activeListeners, listenerAliases = activeAliases)
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
        logger.info(s"${e.getClass.getName} reading configuration from $jarName : $fileName.\n ${e.getMessage}")
        None
    } finally {
      configReader match {
        case Some(reader) => reader.close()
        case None =>
      }
    }
  }

  private[this] def readConfigs(resource: URL): Option[(String, Config)] = {

    // Taking the best guess at the jar name or classpath entry. Should work most of the time.
    val jarName = resource.getProtocol match {
      case "jar" =>
        val jarURL = new URL(resource.getPath.split('!')(0))
        jarURL.getProtocol match {
          case "file" => jarURL.getPath
          case _ => jarURL.toString
        }
      case "file" => // We assume the classpath entry ends before the last /META-INF/
        val path = resource.getPath
        val endIdx = path.lastIndexOf("/META-INF/")
        if (endIdx > 0) path.substring(0, endIdx) else path
      case _ =>
        val path = resource.toString
        val endIdx = path.lastIndexOf("/META-INF/")
        if (endIdx > 0) path.substring(0, endIdx) else path
    }

    try {
      val config = ConfigFactory.parseURL(resource, ConfigParseOptions.defaults().setAllowMissing(false))
      Some((jarName, config))
    } catch {
      case e: Exception =>
        logger.warn(s"${e.getClass.getName} reading configuration from $jarName.\n ${e.getMessage}")
        None
    }
  }

  private[this] def readCube(jarPath: String, config: Config): Option[CubeInit] = {
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

    Some(CubeInit(Cube(cubeAlias, cubeName, cubeVersion, jarPath), c))
  }

  // Resolve cube alias conflict by making it longer on demand.
  @tailrec
  private[unicomplex] def resolveAliasConflicts(cubeList: Seq[CubeInit]): Seq[CubeInit] = {

    val aliasConflicts = cubeList map { cube =>
      (cube.info.name, cube.info.fullName)
    } groupBy (_._1) mapValues { seq =>
      (seq map (_._2)).toSet
    } filter { _._2.size > 1 }

    if (aliasConflicts.isEmpty) cubeList
    else {

      var updated = false

      val newAliases = (aliasConflicts flatMap { case (alias, conflicts) =>
        conflicts.toSeq map { symName =>
          val idx = symName.lastIndexOf('.', symName.length - alias.length - 2)
          if (idx > 0) {
            updated = true
            (symName, symName.substring(idx + 1))
          }
          else (symName, symName)
        }
      }).toSeq

      if (updated) {
        val updatedList = cubeList map { cube =>
          newAliases find { case (symName, alias) => symName == cube.info.fullName } match {
            case Some((symName, alias)) => cube.copy(info = cube.info.copy(name = alias))
            case None => cube
          }
        }
        resolveAliasConflicts(updatedList)
      }
      else sys.error("Duplicate cube names: " + (aliasConflicts flatMap (_._2) mkString ", "))
    }
  }

  private [unicomplex] def startComponents(cube: CubeInit, aliases: Map[String, String])
                                          (implicit actorSystem: ActorSystem) = {
    import cube.components
    import cube.info.{fullName, jarPath, name, version}
    val cubeSupervisor = actorSystem.actorOf(Props[CubeSupervisor], name)
    Unicomplex(actorSystem).uniActor ! CubeRegistration(cube.info, cubeSupervisor)

    def startActor(actorConfig: Config): Option[(String, String, String, Class[_])] = {
      val className = actorConfig getString "class-name"
      val name = actorConfig getOptionalString "name" getOrElse (className substring (className.lastIndexOf('.') + 1))
      val withRouter = actorConfig getOptionalBoolean "with-router" getOrElse false
      val initRequired = actorConfig getOptionalBoolean "init-required" getOrElse false

      try {
        val clazz = Class.forName(className, true, getClass.getClassLoader)
        clazz asSubclass classOf[Actor]

        // Create and the props for this actor to be started, optionally enabling the router.
        val props = if (withRouter) Props(clazz) withRouter FromConfig() else Props(clazz)

        // Send the props to be started by the cube.
        cubeSupervisor ! StartCubeActor(props, name,initRequired)
        Some((fullName, name, version, clazz))
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          logger.warn(s"Can't load actor: $className.\n" +
            s"Cube: $fullName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          None
      }
    }

    def startServiceRoute(clazz: Class[_], proxyName : Option[String], webContext: String, listeners: Seq[String]) = {
      try {
        val routeClass = clazz asSubclass classOf[RouteDefinition]
        val props = Props(classOf[RouteActor], webContext, routeClass)
        val className = clazz.getSimpleName
        val actorName =
          if (webContext.length > 0) s"${webContext.replace('/', '_')}-$className-route"
          else s"root-$className-route"
        cubeSupervisor ! StartCubeService(webContext, listeners, props, actorName, proxyName, initRequired = true)
        Some((fullName, name, version, clazz))
      } catch {
        case e: ClassCastException => None
      }
    }

    // This same creator class is available in Akka's Props.scala but it is inaccessible to us.
    class TypedCreatorFunctionConsumer(clz: Class[_ <: Actor], creator: () => Actor) extends IndirectActorProducer {
      override def actorClass = clz
      override def produce() = creator()
    }

    def startServiceActor(clazz: Class[_], proxyName : Option[String], webContext: String, listeners: Seq[String],
                          initRequired: Boolean) = {
      try {
        val actorClass = clazz asSubclass classOf[Actor]
        def actorCreator: Actor = WebContext.createWithContext[Actor](webContext) { actorClass.newInstance() }
        val props = Props(classOf[TypedCreatorFunctionConsumer], clazz, actorCreator _)
        val className = clazz.getSimpleName
        val actorName =
          if (webContext.length > 0) s"${webContext.replace('/', '_')}-$className-handler"
          else s"root-$className-handler"
        cubeSupervisor ! StartCubeService(webContext, listeners, props, actorName, proxyName, initRequired)
        Some((fullName, name, version, actorClass))
      } catch {
        case e: ClassCastException => None
      }
    }

    def startService(serviceConfig: Config): Option[(String, String, String, Class[_])] =
    try {
      val className = serviceConfig.getString("class-name")
      val clazz = Class.forName(className, true, getClass.getClassLoader)
      val proxyName = serviceConfig.getOptionalString("proxy-name") map (_.trim)
      val webContext = serviceConfig.getString("web-context")

      val listeners = serviceConfig.getOptionalStringList("listeners").fold(Seq("default-listener"))({ list =>

        val listenerMapping = list collect {
          case entry if entry != "*" => (entry, aliases get entry)
        }

        listenerMapping foreach {
          // Make sure we report any missing listeners
          case (entry, None) =>
            logger.warn(s"Listener $entry required by $fullName is not configured. Ignoring.")
          case _ =>
        }

        if (list contains "*") aliases.values.toSeq.distinct
        else listenerMapping collect { case (entry, Some(listener)) => listener }
      })

      val service = startServiceRoute(clazz, proxyName,webContext, listeners) orElse startServiceActor(
        clazz, proxyName, webContext, listeners, serviceConfig getOptionalBoolean "init-required" getOrElse false)

      if (service == None) throw new ClassCastException(s"Class $className is neither a RouteDefinition nor an Actor.")
      service

    } catch {
        case e: Exception =>
          val t = getRootCause(e)
          logger.warn(s"Can't load service definition $serviceConfig.\n" +
            s"Cube: $fullName $version\n" +
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
    logger.info(s"Started cube $fullName $version")
    (actorInfo ++ routeInfo) collect { case Some(component) => component }
  }

  def configuredListeners(config: Config): Map[String, Config] = {
    import collection.JavaConversions._
    val listeners = config.root.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.getOptionalString("type").contains("squbs.listener") => (n, v.toConfig)
    }
    // Check for duplicates
    val listenerMap = mutable.Map.empty[String, Config]
    listeners foreach { case (name, cfg) =>
        listenerMap.get(name) match {
          case Some(_) => logger.warn(s"Duplicate listener $name already declared. Ignoring.")
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
          logger.warn(s"Duplicate alias $alias for listener $listener already declared for listener $l. Ignoring.")
        case None => aliasMap += alias -> listener
      }
    }
    aliasMap.toMap
  }

  def findListeners(config: Config, cubes: Seq[CubeInit]) = {
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
    logger.info(s"Web Service started in $elapsed milliseconds")
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
                          cubes: Seq[CubeInit] = Seq.empty,
                          listeners: Map[String, Config] = Map.empty,
                          listenerAliases: Map[String, String] = Map.empty,
                          jarConfigs: Seq[(String, Config)] = Seq.empty,
                          jarNames: Seq[String] = Seq.empty,
                          actors: Seq[(String, String, String, Class[_])] = Seq.empty,
                          extensions: Seq[Extension] = Seq.empty,
                          started: Boolean = false,
                          stopJVM: Boolean = false) extends LazyLogging {

  import UnicomplexBoot._

  def actorSystemName = config.getString(actorSystemNameKey)

  def actorSystem = UnicomplexBoot.actorSystems(actorSystemName)

  def externalConfigDir = config.getString(extConfigDirKey)

  def createUsing(actorSystemCreator: (String, Config) => ActorSystem) = copy(actorSystemCreator = actorSystemCreator)

  def scanComponents(jarNames: Seq[String]): UnicomplexBoot = scan(jarNames)(this)

  def scanComponents(jarNames: Array[String]): UnicomplexBoot = scan(jarNames.toSeq)(this)

  def scanResources(withClassPath: Boolean, resources: String*): UnicomplexBoot =
    UnicomplexBoot.scanResources(resources map (new File(_).toURI.toURL), withClassPath)(this)

  def scanResources(resources: String*): UnicomplexBoot =
    UnicomplexBoot.scanResources(resources map (new File(_).toURI.toURL), withClassPath = true)(this)

  def initExtensions: UnicomplexBoot = {

    val initSeq = cubes.flatMap { cube =>
      cube.components.getOrElse(StartupType.EXTENSIONS, Seq.empty) map { config =>
        val className = config getString "class-name"
        val seqNo = config getOptionalInt "sequence" getOrElse Int.MaxValue
        (seqNo, className, cube)
      }
    } .sortBy (_._1)

    // load extensions
    val extensions = initSeq map (loadExtension _).tupled

    // preInit extensions
    val preInitExtensions = extensions map extensionOp("preInit", _.preInit())

    // Init extensions
    val initExtensions = preInitExtensions map extensionOp("init", _.init())

    copy(extensions = initExtensions)
  }

  def stopJVMOnExit: UnicomplexBoot = copy(stopJVM = true)

  def start(): UnicomplexBoot = start(defaultStartupTimeout)

  def start(timeout: Timeout): UnicomplexBoot = synchronized {

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
    val actors = cubes.flatMap(startComponents(_, listenerAliases))

    // Start the service infrastructure if services are enabled and registered.
    if (startServices) startServiceInfra(this)

    val postInitExtensions = extensions map extensionOp("postInit", _.postInit())

    // Update the extension errors in Unicomplex actor, in case there are errors.
    uniActor ! Extensions(postInitExtensions)

    {
      // Tell Unicomplex we're done.
      implicit val awaitTimeout = timeout
      val stateFuture = Unicomplex(actorSystem).uniActor ? Activate
      Try(Await.result(stateFuture, awaitTimeout.duration)) recoverWith { case _: TimeoutException =>
        val recoverFuture = Unicomplex(actorSystem).uniActor ? ActivateTimedOut
        Try(Await.result(recoverFuture, awaitTimeout.duration))
      } match {
        case Success(Active) => logger.info(s"[$actorSystemName] activated")
        case Success(Failed) => logger.info(s"[$actorSystemName] initialization failed.")
        case e => logger.warn(s"[$actorSystemName] awaiting confirmation, $e.")
      }
    }

    val boot = copy(config = actorSystem.settings.config, actors = actors, extensions = postInitExtensions, started = true)
    Unicomplex(actorSystem).boot send boot
    boot
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
          extensions.reverse foreach { e =>
            import e.info._
            e.extLifecycle foreach (_.shutdown())
            logger.info(s"Shutting down extension ${e.extLifecycle.getClass.getName} in $fullName $version")
          }
        } onComplete {
          case Success(result) =>
            logger.info(s"ActorSystem ${actorSystem.name} shutdown complete")
            if (stopJVM) System.exit(0)

          case Failure(e) =>
            logger.error(s"Error occurred during shutdown extensions: $e", e)
            if (stopJVM) System.exit(-1)
        }
      }
    }
  }

  def loadExtension(seqNo: Int, className: String, cube: CubeInit): Extension = {
      try {
        val clazz = Class.forName(className, true, getClass.getClassLoader)
        val extLifecycle = ExtensionLifecycle(this) { clazz.asSubclass(classOf[ExtensionLifecycle]).newInstance }
        Extension(cube.info, Some(extLifecycle), Seq.empty)
      } catch {
        case e: Exception =>
          import cube.info._
          val t = getRootCause(e)
          logger.warn(s"Can't load extension $className.\n" +
            s"Cube: $fullName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          Extension(cube.info, None, Seq("load" -> t))
      }
  }

  def extensionOp(opName: String, opFn: ExtensionLifecycle => Unit)
                 (extension: Extension): Extension = {
    import extension.info._

    extension.extLifecycle match {
      case None => extension
      case Some(l) =>
        try {
          opFn(l)
          logger.info(s"Success $opName extension ${l.getClass.getName} in $fullName $version")
          extension
        } catch {
          case e: Exception =>
            val t = getRootCause(e)
            logger.warn(s"Error on $opName extension ${l.getClass.getName}\n"  +
              s"Cube: $fullName $version\n" +
              s"${t.getClass.getName}: ${t.getMessage}")
            t.printStackTrace()
            extension.copy(exceptions = extension.exceptions :+ (opName -> t))
        }
    }
  }
}

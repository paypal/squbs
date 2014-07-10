package org.squbs.unicomplex

import java.io.{FileInputStream, InputStreamReader, Reader, File}
import java.util.{TimerTask, Timer}
import java.util.jar.JarFile

import akka.actor._
import akka.routing.FromConfig
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config._
import org.squbs.lifecycle.ExtensionLifecycle

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import scala.util.{Try, Success, Failure}

import ConfigUtil._
import UnicomplexBoot.Cube

object UnicomplexBoot {

  final val extConfigDirKey = "squbs.external-config-dir"
  final val actorSystemNameKey = "squbs.actorsystem-name"

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
    // 1. See whether add-on config is there.
    addOnConfig match {
      case Some(config) =>
        ConfigFactory.load(config)
      case None =>
        val baseConfig = ConfigFactory.load()
        // Sorry, the configDir is used to read the file. So it cannot be read from this config file.
        val configDir = new File(baseConfig.getString(extConfigDirKey))
        val configFile = new File(configDir, "application")
        val parseOptions = ConfigParseOptions.defaults().setAllowMissing(true)
        val config = ConfigFactory.parseFileAnySyntax(configFile, parseOptions)
        if (config.entrySet.isEmpty) baseConfig else ConfigFactory.load(config)
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
    missingAliases foreach { name => System.err.println(s"Requested listener $name not found!") }
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
        println(s"${e.getClass.getName} reading configuration from $jarName : $fileName.\n ${e.getMessage}")
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

    def startActor(actorConfig: Config): (String, String, String, Class[_]) = {
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
        (symName, alias, version, clazz)
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          println(s"Can't load actor: $className.\n" +
            s"Cube: $symName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          null
      }
    }

    def startRoute(routeConfig: Config): (String, String, String, Class[_]) =
      try {
        val clazz = Class.forName(routeConfig.getString("class-name"), true, getClass.getClassLoader)
        val routeClass = clazz.asSubclass(classOf[RouteDefinition])
        val webContext = routeConfig.getString("web-context")
        val listeners = routeConfig.getOptionalStringList("listeners").fold(Seq("default-listener"))({ list =>

          val listenerMapping = list collect {
            case entry if entry != "*" => (entry, aliases get entry)
          }

          listenerMapping foreach {
            // Make sure we report any missing listeners
            case (entry, None) =>
              System.err.println(s"WARN: Listener $entry required by $symName is not configured. Ignoring.")
            case _ =>
          }

          if (list contains "*") aliases.values.toSeq.distinct
          else listenerMapping collect { case (entry, Some(listener)) => listener }
        })

        val props = Props(classOf[RouteActor], webContext, routeClass)

        cubeSupervisor ! StartCubeService(webContext, listeners, props, webContext + "-route", initRequired = true)
        (symName, alias, version, clazz)
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          println(s"Can't load route definition $routeConfig.\n" +
            s"Cube: $symName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          null
      }

    val actorConfigs = components.getOrElse(StartupType.ACTORS, Seq.empty)
    val routeConfigs = components.getOrElse(StartupType.SERVICES, Seq.empty)

    val actorInfo = actorConfigs map startActor
    val routeInfo = routeConfigs map startRoute

    cubeSupervisor ! Started // Tell the cube all actors to be started are started.
    println(s"Started cube $symName $version")
    actorInfo ++ routeInfo
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
          case Some(_) => System.err.println(s"WARN: Duplicate listener $name already declared. Ignoring.")
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
          System.err.println(s"WARN: Duplicate alias $alias for listener $listener already declared for listener $l. " +
            "Ignoring.")
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
    println(s"Web Service started in $elapsed milliseconds")
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
    } .sortBy (_._2)

    // preInit extensions
    val extensions = initSeq map (preInitExtension _).tupled filter (_ != null)

    // Init extensions
    extensions foreach {
      case (symName, version, extLifecycle) =>
        extLifecycle.init()
        println(s"Started extension ${extLifecycle.getClass.getName} in $symName $version")
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
    val actors = cubes.map(startComponents(_, listenerAliases)).flatten.filter(_ != null)

    // Start the service infrastructure if services are enabled and registered.
    if (startServices) startServiceInfra(this)

    extensions foreach { case (jarName, jarVersion, extLifecycle) => extLifecycle.postInit() }

    {
      // Tell Unicomplex we're done.
      implicit val timeout = Timeout(60 seconds)
      val stateFuture = Unicomplex(actorSystem).uniActor ? Activate
      Try(Await.result(stateFuture, timeout.duration)) match {
        case Success(Active) => println(s"[$actorSystemName] activated")
        case _ => println(s"WARN:[$actorSystemName] initialization failed.")
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
            println(s"Shutting down extension ${extLifecycle.getClass.getName} in $symName $version")
          }
        } onComplete {
          case Success(result) =>
            println(s"ActorSystem ${actorSystem.name} shutdown complete")
            if (stopJVM) System.exit(0)

          case Failure(e) =>
            println(s"Error occurred during shutdown extensions: $e")
            if (stopJVM) System.exit(-1)
        }
      }
    }
  }

  def preInitExtension(seqNo: Int, className: String, symName: String, version: String, jarPath: String):
                      (String, String, ExtensionLifecycle) = {
    try {
      val clazz = Class.forName(className, true, getClass.getClassLoader)
      val extLifecycle = ExtensionLifecycle(this) { clazz.asSubclass(classOf[ExtensionLifecycle]).newInstance }
      extLifecycle.preInit()
      (symName, version, extLifecycle)
    } catch {
      case e: Exception =>
        val t = getRootCause(e)
        println(s"Can't load extension $className.\n" +
          s"Cube: $symName $version\n" +
          s"Path: $jarPath\n" +
          s"${t.getClass.getName}: ${t.getMessage}")
        t.printStackTrace()
        null
    }
  }
}
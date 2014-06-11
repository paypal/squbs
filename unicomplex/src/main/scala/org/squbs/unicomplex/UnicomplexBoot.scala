package org.squbs.unicomplex

import java.io.{FileInputStream, InputStreamReader, Reader, File}
import java.util.{TimerTask, Timer}
import java.util.jar.JarFile
import scala.annotation.tailrec
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.actor._
import akka.routing.FromConfig
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config._
import org.squbs.lifecycle.ExtensionLifecycle
import ConfigUtil._
import scala.collection.concurrent.TrieMap
import scala.util.{Try, Success, Failure}
import scala.collection.mutable

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

  case class InitInfo(jarPath: String, symName: String, alias: String, version: String,
                         entries: Seq[_ <: Config], startupType: StartupType.Value)

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

  private[unicomplex] def scan(jarNames: Seq[String])(obj: UnicomplexBoot):
        UnicomplexBoot = {
    val configEntries = jarNames map readConfigs

    val jarConfigs: Seq[(String, Config)] = jarNames zip configEntries filter (_._2 != None) map {
      case (jar, cfgOption) => (jar, cfgOption.get)
    }

    val initInfoList = jarConfigs.flatMap {
      case (jar, config) => getInitInfo(jar, config)
    }

    val initInfoMap: Map[StartupType.Value, Seq[InitInfo]] = resolveAliasConflicts(initInfoList) groupBy (_.startupType)

    obj.copy(initInfoMap = initInfoMap, jarConfigs = jarConfigs, jarNames = jarNames)
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

  private[this] def getInitInfo(jarPath: String, config: Config): Seq[InitInfo] = {
    val cubeName =
      try {
        config.getString("cube-name")
      } catch {
        case e: ConfigException => return Seq.empty[InitInfo]
      }

    val cubeVersion =
      try {
        config.getString("cube-version")
      } catch {
        case e: ConfigException => return Seq.empty[InitInfo]
      }

    val cubeAlias = cubeName.substring(cubeName.lastIndexOf('.') + 1)

    val initList = mutable.ArrayBuffer.empty[InitInfo]

    val actors = config.getOptionalConfigList("squbs-actors")
    actors foreach { a =>
      if (!a.isEmpty) initList += InitInfo(jarPath, cubeName, cubeAlias, cubeVersion, a, StartupType.ACTORS)
    }

    val routeDefs = config.getOptionalConfigList("squbs-services")
    routeDefs foreach { d =>
      if (!d.isEmpty) initList += InitInfo(jarPath, cubeName, cubeAlias, cubeVersion, d, StartupType.SERVICES)
    }

    val extensions = config.getOptionalConfigList("squbs-extensions")
    
    extensions foreach { e =>
      if (!e.isEmpty) initList += InitInfo(jarPath, cubeName, cubeAlias, cubeVersion, e, StartupType.EXTENSIONS)
    }

    initList.toSeq
  }

  // Resolve cube alias conflict by making it longer on demand.
  @tailrec
  private[unicomplex] def resolveAliasConflicts(initInfoList: Seq[InitInfo]): Seq[InitInfo] = {

    val aliasConflicts = initInfoList map { initInfo =>
      (initInfo.alias, initInfo.symName)
    } groupBy (_._1) mapValues { seq =>
      (seq map (_._2)).toSet
    } filter { _._2.size > 1 }

    if (aliasConflicts.isEmpty) initInfoList
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
        val updatedList = initInfoList map { initInfo =>
          newAliases find { case (symName, alias) => symName == initInfo.symName } match {
            case Some((symName, alias)) => initInfo.copy(alias = alias)
            case None => initInfo
          }
        }
        resolveAliasConflicts(updatedList)
      }
      else sys.error("Duplicate cube names: " + (aliasConflicts flatMap (_._2) mkString ", "))
    }
  }

  private [unicomplex] def startActors(initInfo: InitInfo)(implicit actorSystem: ActorSystem) = {
    import initInfo.{jarPath, symName, alias, version, entries}
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

    val actorInfo = entries map startActor
    cubeSupervisor ! Started // Tell the cube all actors to be started are started.
    println(s"Started cube $symName $version")
    actorInfo
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

  def findListeners(config: Config, services: Seq[InitInfo]) = {
    val demandedListeners =
    for {
      svc <- services
      routes <- svc.entries
      routeListeners <- routes getOptionalStringList "listeners" getOrElse Seq("default-listener")
    } yield {
      routeListeners
    }
    val listeners = configuredListeners(config)
    val aliases = findListenerAliases(listeners)
    val activeAliases = aliases filter { case (n, _) => demandedListeners exists (_ == n) }
    val missingAliases = demandedListeners filterNot { l => activeAliases exists { case (n, _) => n == l } }
    val activeListenerNames = activeAliases.values
    val activeListeners = listeners filter { case (n, c) => activeListenerNames.exists(_ == n)}
    (activeAliases, activeListeners, missingAliases)
  }

  def startServiceInfra(services: Seq[InitInfo])(implicit actorSystem: ActorSystem) = {
    import actorSystem.dispatcher
    val (activeAliases, activeListeners, missingAliases) = findListeners(actorSystem.settings.config, services)
    missingAliases foreach { name => System.err.println(s"Requested listener $name not found!") }
    val startTime = System.nanoTime
    implicit val timeout = Timeout(activeListeners.size.seconds)
    val ackFutures =
      for ((listenerName, config) <- activeListeners) yield {
        Unicomplex(actorSystem).uniActor ? StartWebService(listenerName, config)
      }
    // Block for the web service to be started.
    Await.ready(Future.sequence(ackFutures), timeout.duration)

    val elapsed = (System.nanoTime - startTime) / 1000000
    println(s"Web Service started in $elapsed milliseconds")
    (activeListeners, activeAliases)
  }

  def startRoutes(initInfo: InitInfo, aliases: Map[String, String])(implicit actorSystem: ActorSystem) = {
    import initInfo.{jarPath, symName, alias, version, entries}
    def startRoute(routeConfig: Config): Seq[(String, String, String, RouteDefinition, String)] =
      try {
        val clazz = Class.forName(routeConfig.getString("class-name"), true, getClass.getClassLoader)
        val routeClass = clazz.asSubclass(classOf[RouteDefinition])
        val listeners = routeConfig.getOptionalStringList("listeners").fold(Seq("default-listener"))({ list =>
          val listenerMapping = list map (entry => (entry, aliases get entry))
          listenerMapping foreach {
            // Make sure we report any missing listeners
            case (entry, None) =>
              System.err.println(s"WARN: Listener $entry required by $symName is not configured. Ignoring.")
            case _ =>
          }
          listenerMapping collect { case (entry, Some(listener)) => listener }
        })

        listeners map { listener =>
          val routeInstance = RouteDefinition.startRoutes(actorSystem, listener) { routeClass.newInstance }
          val registrar = Unicomplex(actorSystem).serviceRegistry.registrar()(listener)
          registrar ! Register(symName, alias, version, routeInstance)
          (symName, version, alias, routeInstance, listener)
        }
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

    val routeInfo = entries flatMap startRoute
    println(s"Started routes in $symName $version")
    routeInfo
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
                          initInfoMap: Map[UnicomplexBoot.StartupType.Value,
                            Seq[UnicomplexBoot.InitInfo]] = Map.empty,
                          listeners: Map[String, Config] = Map.empty,
                          listenerAliases: Map[String, String] = Map.empty,
                          jarConfigs: Seq[(String, Config)] = Seq.empty,
                          jarNames: Seq[String] = Seq.empty,
                          actors: Seq[(String, String, String, Class[_])] = Seq.empty,
                          services: Seq[(String, String, String, RouteDefinition, String)] = Seq.empty,
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

    // preInit extensions
    val initSeq = initInfoMap.getOrElse(StartupType.EXTENSIONS, Seq.empty[InitInfo]).
      map(getExtensionList).flatten.sortBy { case (_, _, seqNo) => seqNo}

    val extensions = initSeq map {
      case (initInfo, className, seqNo) => preInitExtension(initInfo, className, seqNo)
    } filter (_ != null)

    // Init extensions
    extensions foreach {
      case (symName, version, extLifecycle) =>
        extLifecycle.init(jarConfigs)
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

    // Get the actors to start
    val actorsToStart = initInfoMap.getOrElse(StartupType.ACTORS, Seq.empty[InitInfo])

    // Get the services to start
    val startService = Unicomplex(actorSystem).config getBoolean "start-service"
    val servicesToStart =
      if (startService) initInfoMap.getOrElse(StartupType.SERVICES, Seq.empty)
      else Seq.empty

    // Notify Unicomplex that services will be started.
    if (!servicesToStart.isEmpty) uniActor ! PreStartWebService

    // Signal started to Unicomplex.
    uniActor ! Started

    // Start all actors
    val actors = actorsToStart.map(startActors).flatten.filter(_ != null)

    // Start the service infrastructure if services are enabled and registered.

    val (listeners, aliases) =
      if (!servicesToStart.isEmpty) startServiceInfra(servicesToStart)
      else (Map.empty[String, Config], Map.empty[String, String])

    // Start all service routes
    val services = servicesToStart.map(startRoutes(_, aliases)).flatten.filter(_ != null)


    // Queue message on registrar which will then forwarded to Unicomplex when all services are processed.
    // Prevents out of band notification.
    if (!servicesToStart.isEmpty)
      Unicomplex(actorSystem).serviceRegistry.registrar().values foreach (_ ! RoutesStarted)

    extensions foreach { case (jarName, jarVersion, extLifecycle) => extLifecycle.postInit(jarConfigs) }

    Unicomplex(actorSystem).uniActor ! Activate // Tell Unicomplex we're done.

    // Make sure we wait for Unicomplex to be started properly before completing the start.
    implicit val timeout = Timeout(1.seconds)

    var state: LifecycleState = Starting
    var retries = 0

    while (state != Active && state != Failed && retries < 100) {
      val stateFuture = (Unicomplex(actorSystem).uniActor ? SystemState).mapTo[LifecycleState]
      state = Try(Await.result(stateFuture, timeout.duration)) getOrElse Starting
      if (state != Active && state != Failed) {
        Thread.sleep(1000)
        retries += 1
      }
    }

    if (state != Active && state != Failed) throw new InstantiationException(
      s"Unicomplex not entering 'Active' or 'Failed' state. Stuck at '$state' state. Timing out.")
    if (state == Failed)
      println("WARN: Unicomplex initialization: Some cubes failed to initialize")


    copy(config = actorSystem.settings.config, actors = actors, services = services,
      listeners = listeners,listenerAliases = aliases, extensions = extensions, started = true)
  }

  def registerExtensionShutdown(actorSystem: ActorSystem) {
    if (!extensions.isEmpty) {
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
            extLifecycle.shutdown(jarConfigs)
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

  def getExtensionList(initInfo: InitInfo): Seq[(InitInfo, String, Int)] = {
    initInfo.entries map { config =>
      val className = config.getString("class-name")
      val seqNo = config.getOptionalInt("sequence").getOrElse(Int.MaxValue)
      (initInfo, className, seqNo)
    }
  }

  def preInitExtension(initInfo: InitInfo, extension: String, seq: Int): (String, String, ExtensionLifecycle) = {
    import initInfo.{symName, version, jarPath}
    try {
      val clazz = Class.forName(extension, true, getClass.getClassLoader)
      val extLifecycle = ExtensionLifecycle(this) { clazz.asSubclass(classOf[ExtensionLifecycle]).newInstance }
      extLifecycle.preInit(jarConfigs)
      (symName, version, extLifecycle)
    } catch {
      case e: Exception =>
        val t = getRootCause(e)
        println(s"Can't load extension $extension.\n" +
          s"Cube: $symName $version\n" +
          s"Path: $jarPath\n" +
          s"${t.getClass.getName}: ${t.getMessage}")
        t.printStackTrace()
        null
    }
  }
}
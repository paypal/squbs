package org.squbs.unicomplex

import java.io.{FileInputStream, InputStreamReader, Reader, File}
import java.util.{TimerTask, Timer}
import java.util.jar.JarFile
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.actor._
import akka.routing.FromConfig
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.{ConfigException, ConfigFactory, Config}
import org.squbs.lifecycle.ExtensionLifecycle
import ConfigUtil._
import scala.Some
import java.util.concurrent.TimeoutException
import scala.collection.concurrent.TrieMap

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
        val configFile = new File(configDir, "application.conf")
        if (configFile.exists) ConfigFactory.load(ConfigFactory.parseFile(configFile))
        else ConfigFactory.load()
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

    val initList = ArrayBuffer.empty[InitInfo]

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

  def startServiceInfra()(implicit actorSystem: ActorSystem) {
    val startTime = System.nanoTime
    implicit val timeout = Timeout(1.second)
    val ackFuture = Unicomplex(actorSystem).uniActor ? StartWebService
    // Block for the web service to be started.
    Await.ready(ackFuture, timeout.duration)
    // Tight loop making sure the registrar is in place
    val registry = Unicomplex(actorSystem).serviceRegistry
    import registry._

    val retry = 1000
    var count = 0

    while (serviceActorContext() == null && count < retry) {
      count += 1
      println("waiting: " + count)
      Await.result(serviceActorContext.future(), timeout.duration)
    }
    if (count == retry) throw new TimeoutException(s"Timing out service creation after $retry waits.")

    val elapsed = (System.nanoTime - startTime) / 1000000
    println(s"Web Service started in $elapsed milliseconds")
  }

  def startRoutes(initInfo: InitInfo)(implicit actorSystem: ActorSystem) = {
    import initInfo.{jarPath, symName, alias, version, entries}
    def startRoute(routeConfig: Config): (String, String, String, RouteDefinition) =
      try {
        val clazz = Class.forName(routeConfig.getString("class-name"), true, getClass.getClassLoader)
        val routeClass = clazz.asSubclass(classOf[RouteDefinition])
        val routeInstance = RouteDefinition.startRoutes(actorSystem) { routeClass.newInstance }
        Unicomplex(actorSystem).serviceRegistry.registrar() ! Register(symName, alias, version, routeInstance)
        (symName, version, alias, routeInstance)
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

    val routeInfo = entries map startRoute
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
                          jarConfigs: Seq[(String, Config)] = Seq.empty,
                          jarNames: Seq[String] = Seq.empty,
                          actors: Seq[(String, String, String, Class[_])] = Seq.empty,
                          services: Seq[(String, String, String, RouteDefinition)] = Seq.empty,
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
    if (!servicesToStart.isEmpty) startServiceInfra()

    // Start all service routes
    val services = servicesToStart.map(startRoutes).flatten.filter(_ != null)


    // Queue message on registrar which will then forwarded to Unicomplex when all services are processed.
    // Prevents out of band notification.
    if (!servicesToStart.isEmpty) Unicomplex(actorSystem).serviceRegistry.registrar() ! WebServicesStarted

    extensions foreach { case (jarName, jarVersion, extLifecycle) => extLifecycle.postInit(jarConfigs) }

    copy(config = actorSystem.settings.config, actors = actors, services = services, extensions = extensions,
      started = true)
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
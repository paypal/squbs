package org.squbs.unicomplex

import java.io._
import java.util.jar.JarFile
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.actor.{ActorSystem, ActorRef, Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.squbs.lifecycle.{GracefulStop, ExtensionLifecycle}
import com.typesafe.config.{ConfigException, ConfigFactory, Config}
import ConfigUtil._
import akka.routing.FromConfig
import scala.annotation.tailrec
import org.squbs.util.conversion.CubeUtil._
import scala.util.{Failure, Success}
import java.util.{TimerTask, Timer}

object Bootstrap extends App {

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

  // Make it extra lazy, so that we do not create the next File if the previous one succeeds.
  val configExtensions = Stream("conf", "json", "properties")


  println("Booting unicomplex")
  
  val startTime = Timestamp(System.nanoTime, System.currentTimeMillis)

  val startTimeMillis = System.currentTimeMillis

  val cpEntries = System.getProperty("java.class.path").split(File.pathSeparator).toSeq

  val configEntries = cpEntries map readConfigs

  val jarConfigs: Seq[(String, Config)] = cpEntries zip configEntries filter (_._2 != None) map {
    case (jar, cfgOption) => (jar, cfgOption.get)
  }

  val initInfoList = jarConfigs flatMap {
    case (jar, config) => getInitInfo(jar, config)
  }

  val initInfoMap = resolveAliasConflicts(initInfoList) groupBy (_.startupType)

  // preInit extensions
  val initSeq = initInfoMap.getOrElse(StartupType.EXTENSIONS, Seq.empty[InitInfo]).
    map(getExtensionList).flatten.sortBy{ case (_, _, seqNo) => seqNo }

  val extensions = initSeq map {
    case (initInfo, className, seqNo) => preInitExtension(initInfo, className, seqNo)} filter (_ != null)

  // Init extensions
  extensions foreach {
    case (symName, version, extLifecycle) =>
      extLifecycle.init(jarConfigs)
      println(s"Started extension ${extLifecycle.getClass.getName} in $symName $version")
  }

  // Send start time to Unicomplex
  Unicomplex() ! startTime

  // Register for stopping the extensions
  Unicomplex.actorSystem.registerOnTermination {
    // Run the shutdown in a different thread, not in the ActorSystem's onTermination thread.
    import scala.concurrent.Future

    // Kill the JVM if the shutdown takes longer than the timeout.
    val shutdownTimer = new Timer(true)
    shutdownTimer.schedule(new TimerTask { def run() { System.exit(0) }},
      Unicomplex.config.getMilliseconds("shutdown-timeout"))

    // Then run the shutdown in the global execution context.
    Future {
      extensions.reverse foreach { case (symName, version, extLifecycle) =>
        extLifecycle.shutdown(jarConfigs)
        println(s"Shutting down extension ${extLifecycle.getClass.getName} in $symName $version")
      }
    } (scala.concurrent.ExecutionContext.global)
  }

  // Signal started to Unicomplex.
  Unicomplex() ! Started

  // Start all actors
  val actors     = initInfoMap.getOrElse(StartupType.ACTORS, Seq.empty[InitInfo]).map(startActors)
    .flatten.filter(_ != null)

  // Start all service routes
  private val startService = Unicomplex.config getBoolean "start-service"
  val services =
    if (startService)
      initInfoMap.getOrElse(StartupType.SERVICES, Seq.empty[InitInfo]).map(startRoutes).flatten.filter(_ != null)
    else Seq.empty

  // postInit extensions
  extensions foreach { case (jarName, jarVersion, extLifecycle) => extLifecycle.postInit(jarConfigs)}

  private[this] def readConfigs(jarName: String): Option[Config] = {
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
    val cubeName =   // TODO: Read name and version from maven and/or ivy metadata
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
  def resolveAliasConflicts(initInfoList: Seq[InitInfo]): Seq[InitInfo] = {

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

  def startActors(initInfo: InitInfo) = {
    import initInfo.{jarPath, symName, alias, version, entries}
    val cubeSupervisor = Unicomplex.actorSystem.actorOf(Props[CubeSupervisor], alias)
    Unicomplex() ! CubeRegistration(alias, symName, version, cubeSupervisor)

    def startActor(actorConfig: Config): (String, String, Class[_]) = {
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
        (symName, version, clazz)
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          println(s"Can't load actor: $className.\n" +
            s"Cube: $symName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          null
      }
    }

    val actorInfo = entries map startActor
    cubeSupervisor ! Started // Tell the cube all actors to be started are started.
    println(s"Started cube $symName $version")
    actorInfo
  }

  def startRoutes(initInfo: InitInfo) = {
    import initInfo.{jarPath, symName, version, entries}
    def startRoute(routeConfig: Config): (String, String, RouteDefinition) =
      try {
        import ServiceRegistry.registrar
        val clazz = Class.forName(routeConfig.getString("class-name"), true, getClass.getClassLoader)
        val routeClass = clazz.asSubclass(classOf[RouteDefinition])
        if (registrar() == null) {
          val startTime = System.nanoTime
          implicit val timeout = Timeout(1000 milliseconds)
          val ackFuture = Unicomplex() ? StartWebService
          // Block for the web service to be started.
          Await.ready(ackFuture, timeout.duration)
          // Tight loop making sure the registrar is in place
          while (registrar() == null) {
            Await.result(registrar.future(), timeout.duration)
          }
          val elapsed = (System.nanoTime - startTime) / 1000000
          println(s"Web Service started in $elapsed milliseconds")
        }
        val routeInstance = routeClass.newInstance
        registrar() ! Register(routeInstance)
        (symName, version, routeInstance)
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          println(s"Can't load route definition $routeConfig.\n" +
            s"Cube: $symName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          null
      }

    val routeInfo = entries map startRoute
    println(s"Started routes in $symName $version")
    routeInfo
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
      val extLifecycle = clazz.asSubclass(classOf[ExtensionLifecycle]).newInstance
      extLifecycle.preInit(jarConfigs)
      (symName, version, extLifecycle)
    } catch {
      case e: Exception =>
        val t = getRootCause(e)
        println(s"Can't load extension $extension.\n" +
          s"Cube: $symName $version\n" +
          s"Path: $jarPath\n" +
          s"${t.getClass.getName}: ${t.getMessage}")
        null
    }
  }

  def shutdownSystem: Unit = {
    Unicomplex() ! GracefulStop
  }

  private[this] def parseOptions(options: String): Array[(String, String)] =
    options.split(';').map { nv =>
      val eqIdx = nv.indexOf('=')
      val name = nv.substring(0, eqIdx)
      val value = nv.substring(eqIdx + 1)
      name -> value        
    }      

  
  private[this] def getRootCause(e: Exception) = {
    var t : Throwable = e
    var cause = e.getCause
    while (cause != null) {
      t = cause
      cause = t.getCause
    }
    t
  }

  // TODO use the following in hot deployment
  private[unicomplex] def stopCube(cubeName: String)(implicit system: ActorSystem): Future[ActorRef] = {
    implicit val executionContext = system.dispatcher

    // Stop the extensions of this cube if there are any
    extensions.filter(_._1 == cubeName).map(_._3).foreach(extension => {/* stop the extension */})

    // Unregister the routes of this cube if there are any
    services.filter(_._1 == cubeName).map(_._3).foreach(routeDef => {
      ServiceRegistry.registrar() ! Unregister(routeDef.webContext)
    })

    // Stop the CubeSupervisor if there is one
    nameToAlias(cubeName).cubeSupervisor()
  }

  private[unicomplex] def startCube(cubeName: String)(implicit system: ActorSystem): Unit = {
    implicit val executionContext = system.dispatcher

    // TODO prevent starting an active Cube
    // PreInit extentions if there are any
    val extensionsInCube = extensions.filter(_._1 == cubeName).map(_._3)
    extensionsInCube.foreach(_.preInit(jarConfigs))
    // Init extentions
    extensionsInCube.foreach(_.init(jarConfigs))

    // Start actors if there are any
    nameToAlias(cubeName).cubeSupervisor().onComplete({
      case Success(supervisor) => println(s"[warn][Bootstrap] actors in $cubeName are already activated")

      case Failure(_) =>
        initInfoMap.getOrElse(StartupType.ACTORS, Seq.empty[Bootstrap.InitInfo])
          .filter(_.symName == cubeName).foreach(startActors)
    })

    // Start services if there are any
    services.filter(_._1 == cubeName).map(_._3).foreach(routeDef => {
      ServiceRegistry.registrar() ! Register(routeDef)
    })

    // PostInit
    extensionsInCube.foreach(_.postInit(jarConfigs))
  }

  // lookup the cube alias according to the cube name
  private def nameToAlias(cubeName: String): String = {
    initInfoMap.getOrElse(StartupType.ACTORS, Seq.empty[InitInfo]).find(_.symName == cubeName) match {
      case Some(info) => info.alias

      case None => ""
    }
  }
}

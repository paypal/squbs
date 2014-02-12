package org.squbs.unicomplex

import java.io._
import java.util.jar.JarFile
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.squbs.lifecycle.ExtensionInit
import com.typesafe.config.{ConfigException, ConfigFactory, Config}
import ConfigUtil._
import akka.routing.FromConfig

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

  case class InitInfo(jarPath: String, symName: String, version: String,
                      entries: Seq[_ <: Config], startupType: StartupType.Value)

  // Make it extra lazy, so that we do not create the next File if the previous one succeeds.
  val configExtensions = Stream("conf", "json", "properties")


  println("Booting unicomplex")
  
  val startTime = System.nanoTime

  val cpEntries = System.getProperty("java.class.path").split(File.pathSeparator).toSeq

  val configEntries = cpEntries map readConfigs

  val jarConfigs: Seq[(String, Config)] = cpEntries zip configEntries filter (_._2 != None) map {
    case (jar, cfgOption) => (jar, cfgOption.get)
  }

  val initInfoMap = jarConfigs flatMap {
    case (jar, config) => getInitInfo(jar, config)
  } groupBy (_.startupType)

  // preInit extensions
  val initSeq = initInfoMap.getOrElse(StartupType.EXTENSIONS, Seq.empty[InitInfo]).
    map(getExtensionList).flatten.sortBy{ case (_, _, seqNo) => seqNo }

  val extensions = initSeq map {
    case (initInfo, className, seqNo) => preInitExtension(initInfo, className, seqNo)} filter (_ != null)

  // Init extensions
  extensions foreach {
    case (symName, version, extensionInit) =>
      extensionInit.init(jarConfigs)
      println(s"Started extension ${extensionInit.getClass.getName} in $symName $version")
  }

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
  extensions foreach { case (jarName, jarVersion, extensionInit) => extensionInit.postInit(jarConfigs)}


  val elapsed = (System.nanoTime - startTime) / 1000000
  println(s"squbs started in $elapsed milliseconds")

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

    val initList = ArrayBuffer.empty[InitInfo]

    val actors = config.getOptionalConfigList("squbs-actors")
    actors foreach { a =>
      if (!a.isEmpty) initList += InitInfo(jarPath, cubeName, cubeVersion, a, StartupType.ACTORS)
    }

    val routeDefs = config.getOptionalConfigList("squbs-services")
    routeDefs foreach { d =>
      if (!d.isEmpty) initList += InitInfo(jarPath, cubeName, cubeVersion, d, StartupType.SERVICES)
    }
    
    val extensions = config.getOptionalConfigList("squbs-extensions")
    extensions foreach { e =>
      if (!e.isEmpty) initList += InitInfo(jarPath, cubeName, cubeVersion, e, StartupType.EXTENSIONS)
    }
    
    initList.toSeq
  }

  def startActors(initInfo: InitInfo) = {
    import initInfo.{jarPath, symName, version, entries}
    import Unicomplex.actorSystem
    val cubeActor = actorSystem.actorOf(Props[CubeSupervisor],
      symName.substring(symName.lastIndexOf('.') + 1))

    def startActor(actorConfig: Config): (String, String, Class[_]) = {
      val className = actorConfig.getString("class-name")
      val name = actorConfig.getOptionalString("name").getOrElse(className.substring(className.lastIndexOf('.') + 1))

      try {
        val clazz = Class.forName(className, true, getClass.getClassLoader)
        val actorClass = clazz.asSubclass(classOf[Actor])

        // Create and configure the props for this actor to be started.
        var props = Props(actorClass)
        actorConfig getOptionalString "dispatcher" foreach (d => props = props withDispatcher d)
        actorConfig getOptionalString "mailbox" foreach (m => props = props withMailbox m)
        val withRouter = actorConfig getOptionalBoolean "with-router" getOrElse false
        if (withRouter) props = props withRouter FromConfig()

        // Send the props to be started by the cube.
        cubeActor ! StartCubeActor(props, name)
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
    println(s"Started cube $symName $version")
    actorInfo
  }

  def startRoutes(initInfo: InitInfo) = {
    import initInfo.{jarPath, symName, version, entries}
    def startRoute(routeConfig: Config): (String, String, Class[_]) =
      try {
        import ServiceRegistry.registrar
        val clazz = Class.forName(routeConfig.getString("class-name"), true, getClass.getClassLoader)
        val routeClass = clazz.asSubclass(classOf[RouteDefinition])
        if (registrar() == null) {
          val startTime = System.nanoTime
          implicit val timeout = Timeout(1000 milliseconds)
          val ackFuture = Unicomplex.uniActor ? StartWebService
          // Block for the web service to be started.
          Await.ready(ackFuture, timeout.duration)
          // Tight loop making sure the registrar is in place
          while (registrar() == null) {
            Await.result(registrar.future(), timeout.duration)
          }
          val elapsed = (System.nanoTime - startTime) / 1000000
          println(s"Web Service started in $elapsed milliseconds")
        }
        registrar() ! Register(routeClass.newInstance)
        (symName, version, clazz)
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

    def preInitExtension(initInfo: InitInfo, extension: String, seq: Int): (String, String, ExtensionInit) = {
      import initInfo.{symName, version, jarPath}
      try {
        val clazz = Class.forName(extension, true, getClass.getClassLoader)
        val extensionInit = clazz.asSubclass(classOf[ExtensionInit]).newInstance
        extensionInit.preInit(jarConfigs)
        (symName, version, extensionInit)
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
}

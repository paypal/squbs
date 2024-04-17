/*
 *  Copyright 2017 PayPal
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

import java.io._
import java.net.URL
import java.util.concurrent.{TimeUnit, TimeoutException}
import java.util.jar.JarFile
import java.util.{Timer, TimerTask}

import org.apache.pekko.actor._
import org.apache.pekko.pattern.ask
import org.apache.pekko.routing.FromConfig
import org.apache.pekko.util.Timeout
import com.typesafe.config._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.lifecycle.ExtensionLifecycle
import org.squbs.pipeline.PipelineSetting
import org.squbs.unicomplex.{Extension => SqubsExtension}
import org.squbs.unicomplex.UnicomplexBoot.CubeInit
import org.squbs.util.ConfigUtil._

import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{postfixOps, existentials}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object UnicomplexBoot extends LazyLogging {

  final val extConfigDirKey = "squbs.external-config-dir"
  final val extConfigNameKey = "squbs.external-config-files"
  final val actorSystemNameKey = "squbs.actorsystem-name"

  val defaultStartupTimeout: Timeout =
    Try(System.getProperty("startup.timeout").toLong) map { millis =>
      org.apache.pekko.util.Timeout(millis, TimeUnit.MILLISECONDS)
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

  def apply(actorSystemCreator: (String, Config) => ActorSystem): UnicomplexBoot = {
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
        val configNames = baseConfig.getStringList(extConfigNameKey)
        configNames.add("application")
        val parseOptions = ConfigParseOptions.defaults().setAllowMissing(true)
        val addConfigs = configNames.asScala.map {
          name => ConfigFactory.parseFileAnySyntax(new File(configDir, name), parseOptions)
        }
        if (addConfigs.isEmpty) baseConfig
        else ConfigFactory.load(addConfigs.foldRight(baseConfig){ _ withFallback _ })
    }
  }

  private[unicomplex] def scan(jarNames: Seq[String])(boot: UnicomplexBoot): UnicomplexBoot = {
    val configEntries = jarNames map readConfigs
    val jarConfigs = jarNames zip configEntries collect { case (jar, Some(cfg)) => (jar, cfg) }
    resolveCubes(jarConfigs, boot.copy(jarNames = jarNames))
  }

  private[unicomplex] def scanResources(resources: Seq[URL],
                                        withClassPath: Boolean = true)(boot: UnicomplexBoot): UnicomplexBoot = {
    val cpResources: Seq[URL] =
      if (withClassPath) {
        val loader = getClass.getClassLoader
        Seq("conf", "json", "properties").flatMap { ext => loader.getResources(s"META-INF/squbs-meta.$ext").asScala }
      } else Seq.empty

    // Dedup the resources, just in case.
    val allResources = mutable.LinkedHashSet(cpResources ++ resources : _*).toSeq
    val jarConfigs = allResources map readConfigs collect { case Some(jarCfg) => jarCfg }
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

  private def createReaderFromFS(directory: File): String => Option[Reader] = {
    (filePath: String) => Option(new File(directory, filePath)) collect {
      case configFile if configFile.isFile => new InputStreamReader(new FileInputStream(configFile), "UTF-8")
    }
  }

  private def createReaderFromJarFile(file: File): String => Option[Reader] = {
    val triedJarFile = Try(new JarFile(file))
    (filePath: String) => triedJarFile match {
      case Success(jarFile) => Option(jarFile.getEntry(filePath)) collect {
        case configFile if !configFile.isDirectory => new InputStreamReader(jarFile.getInputStream(configFile), "UTF-8")
      }
      case Failure(e)       => throw e
      }
  }

  private def getConfigReader(jarName: String): Option[(Option[Reader], String)] = {
    // Make it extra lazy, so that we do not create the next File if the previous one succeeds.
    val configExtensions = Iterator("conf", "json", "properties")
    val maybeConfFileReader = Option(new File(jarName)) collect {
      case file if file.isDirectory => createReaderFromFS(file)
      case file if file.isFile      => createReaderFromJarFile(file)
    }

    maybeConfFileReader flatMap (fileReader => configExtensions map { ext =>
      val currentFile = s"META-INF/squbs-meta.$ext"
      Try(fileReader(currentFile)) match {
        case Failure(e) =>
          logger.info(s"${e.getClass.getName} reading configuration from $jarName : $currentFile.\n${e.getMessage}")
          None
        case Success(maybeReader) => Option(maybeReader, currentFile)
      }
    } find (_.isDefined) flatten)
  }

  private[this] def readConfigs(jarName: String): Option[Config] = {
    getConfigReader(jarName) flatMap ((maybeReader: Option[Reader], fileName: String) => {
      val maybeConfig = Try(maybeReader map ConfigFactory.parseReader) match {
        case Failure(e)   =>
          logger.info(s"${e.getClass.getName} reading configuration from $jarName : $fileName.\n${e.getMessage}")
          None
        case Success(cfg) => cfg
      }
      maybeReader foreach(_.close())
      maybeConfig
    }).tupled
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
      case NonFatal(e) =>
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


    val c = Seq(
        config.getOption[Seq[Config]]("squbs-actors") map ((StartupType.ACTORS, _)),
        config.getOption[Seq[Config]]("squbs-services") map ((StartupType.SERVICES, _)),
        config.getOption[Seq[Config]]("squbs-extensions") map ((StartupType.EXTENSIONS, _))
      ).collect { case Some((sType, configs)) => (sType, configs) }.toMap

    Some(CubeInit(Cube(cubeAlias, cubeName, cubeVersion, jarPath), c))
  }

  // Resolve cube alias conflict by making it longer on demand.
  @tailrec
  private[unicomplex] def resolveAliasConflicts(cubeList: Seq[CubeInit]): Seq[CubeInit] = {

    val aliasConflicts = cubeList
      .map { cube =>
        (cube.info.name, cube.info.fullName)
      }
      .groupBy (_._1)
      .map { case (name, seq) => name -> (seq map (_._2)).toSet }
      .filter { _._2.size > 1 }

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

  private[unicomplex] def startComponents(cube: CubeInit, aliases: Map[String, String])
                                         (implicit actorSystem: ActorSystem,
                                          timeout: Timeout = UnicomplexBoot.defaultStartupTimeout) = {
    import cube.components
    import cube.info.{fullName, jarPath, name, version}
    val cubeSupervisor = actorSystem.actorOf(Props[CubeSupervisor](), name)
    Unicomplex(actorSystem).uniActor ! CubeRegistration(cube.info, cubeSupervisor)

    def startActor(actorConfig: Config): Option[(String, String, String, Class[_])] = {
      val className = actorConfig getString "class-name"
      val name = actorConfig.get[String]("name", className substring (className.lastIndexOf('.') + 1))
      val withRouter = actorConfig.get[Boolean]("with-router", false)
      val initRequired = actorConfig.get[Boolean]("init-required", false)

      try {
        val clazz = Class.forName(className, true, getClass.getClassLoader)
        clazz asSubclass classOf[Actor]

        // Create and the props for this actor to be started, optionally enabling the router.
        val props = if (withRouter) Props(clazz) withRouter FromConfig() else Props(clazz)

        // Send the props to be started by the cube.
        cubeSupervisor ! StartCubeActor(props, name, initRequired)
        Some((fullName, name, version, clazz))
      } catch {
        case NonFatal(e) =>
          val t = getRootCause(e)
          logger.warn(s"Can't load actor: $className.\n" +
            s"Cube: $fullName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          cubeSupervisor ! StartFailure(e)
          None
      }
    }

    def startServiceRoute(clazz: Class[_], webContext: String, listeners: Seq[String],
                          ps: PipelineSetting) = {

      Try {
        (clazz asSubclass classOf[RouteDefinition], classOf[RouteActor])
      } orElse Try {
        (clazz asSubclass classOf[FlowDefinition], classOf[FlowActor])
      } orElse Try {
        (clazz asSubclass classOf[AbstractRouteDefinition], classOf[JavaRouteActor])
      } orElse Try {
        (clazz asSubclass classOf[AbstractFlowDefinition], classOf[JavaFlowActor])
      } map { case (routeClass, routeActor) =>
        val props = Props(routeActor, webContext, routeClass)
        val className = clazz.getSimpleName
        val actorName =
          if (webContext.length > 0) s"${webContext.replace('/', '_')}-$className-route"
          else s"root-$className-route"
        cubeSupervisor ! StartCubeService(webContext, listeners, props, actorName, ps, initRequired = true)
        (fullName, name, version, clazz)
      }
    }

    // This same creator class is available in Pekko's Props.scala but it is inaccessible to us.
    class TypedCreatorFunctionConsumer(clz: Class[_ <: Actor], creator: () => Actor) extends IndirectActorProducer {
      override def actorClass = clz

      override def produce() = creator()
    }

    def startServiceActor(clazz: Class[_], webContext: String, listeners: Seq[String],
                          ps: PipelineSetting, initRequired: Boolean) =
      Try {
        val actorClass = clazz asSubclass classOf[Actor]
        def actorCreator: Actor = WithWebContext(webContext) { actorClass.newInstance() }
        val props = Props(classOf[TypedCreatorFunctionConsumer], clazz, () => actorCreator)
        val className = clazz.getSimpleName
        val actorName =
          if (webContext.length > 0) s"${webContext.replace('/', '_')}-$className-handler"
          else s"root-$className-handler"
        cubeSupervisor ! StartCubeService(webContext, listeners, props, actorName, ps, initRequired)
        (fullName, name, version, actorClass)
      }

    def startService(serviceConfig: Config): Option[(String, String, String, Class[_])] =
      Try {
        val className = serviceConfig.getString("class-name")
        val clazz = Class.forName(className, true, getClass.getClassLoader)
        val webContext = serviceConfig.getString("web-context")
        val pipeline = serviceConfig.getOption[String]("pipeline")
        val defaultFlowsOn = serviceConfig.getOption[Boolean]("defaultPipeline")
        val pipelineSettings = (pipeline, defaultFlowsOn)

        val listeners = serviceConfig.getOption[Seq[String]]("listeners").fold(Seq("default-listener")) { list =>
          if (list.contains("*")) aliases.values.toSeq.distinct
          else list flatMap { entry =>
            aliases.get(entry) match {
              case Some(listener) => Seq(listener)
              case None =>
                logger.warn(s"Listener $entry required by $fullName is not configured. Ignoring.")
                Seq.empty[String]
            }
          }
        }

        val service = startServiceRoute(clazz, webContext, listeners, pipelineSettings) orElse
          startServiceActor(clazz, webContext, listeners, pipelineSettings,
            serviceConfig.get[Boolean]("init-required", false))
        service match {
          case Success(svc) => svc
          case Failure(e) =>
            throw new IOException(s"Class $className is neither a RouteDefinition nor an Actor.", e)
        }
      } match {
        case Success(svc) => Some(svc)
        case Failure(e) =>
          val t = getRootCause(e)
          logger.warn(s"Can't load service definition $serviceConfig.\n" +
            s"Cube: $fullName $version\n" +
            s"Path: $jarPath\n" +
            s"${t.getClass.getName}: ${t.getMessage}")
          t.printStackTrace()
          cubeSupervisor ! StartFailure(e)
          None
      }

    val actorConfigs = components.getOrElse(StartupType.ACTORS, Seq.empty)
    val routeConfigs = components.getOrElse(StartupType.SERVICES, Seq.empty)

    val actorInfo = actorConfigs map startActor
    val routeInfo = routeConfigs map startService

    val startedF = cubeSupervisor ? Started // Tell the cube all actors to be started are started.
    logger.info(s"Started cube $fullName $version")
    val componentInfo = (actorInfo ++ routeInfo) collect { case Some(component) => component }
    (startedF, componentInfo)
  }

  def configuredListeners(config: Config): Map[String, Config] = {
    val listeners = config.root.asScala.toSeq.collect {
      case (n, v: ConfigObject) if v.toConfig.getOption[String]("type").contains("squbs.listener") => (n, v.toConfig)
    }

    resolveDuplicates[Config](listeners, (name, conf, c) =>
      logger.warn(s"Duplicate listener $name already declared. Ignoring.")
    )
  }

  def findListenerAliases(listeners: Map[String, Config]): Map[String, String] = {
    val aliases = for ((name, config) <- listeners) yield {
      val aliasNames = config.get[Seq[String]]("aliases", Seq.empty[String])
      (name, name) +: (aliasNames map ((_, name)))
    }

    resolveDuplicates[String](aliases.toSeq.flatten, (alias, listener, l) =>
      logger.warn(s"Duplicate alias $alias for listener $listener already declared for listener $l. Ignoring.")
    )
  }

  def resolveDuplicates[T](in: Seq[(String, T)], duplicateHandler: (String, T, T) => Unit): Map[String, T] = {
    in.groupBy(_._1).map {
      case (key, Seq((k, v))) => key -> v
      case (key, head::tail) =>
        tail.foreach { case (k, ass) => duplicateHandler(k, ass, head._2)}
        key -> head._2
    }
  }

  def findListeners(config: Config, cubes: Seq[CubeInit]) = {
    val demandedListeners =
      for {
        routes <- cubes.map { _.components.get(StartupType.SERVICES) }.collect { case Some(routes) => routes }.flatten
        routeListeners <- routes.get[Seq[String]]("listeners", Seq("default-listener"))
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

  def startServiceInfra(boot: UnicomplexBoot)(implicit actorSystem: ActorSystem): Unit = {

    def getTimeout(keyRelPath: String): Option[Timeout] = {
      val key = s"squbs.service-infra.$keyRelPath"
      val timeoutDuration = actorSystem.settings.config.getOptionalDuration(key)
      timeoutDuration.map { d => 
        require(d.toMillis > 0, s"The config property, $key, must be greater than 0 milliseconds.")
        Timeout(d)
      }
    }

    val overallTimeout = getTimeout("timeout").getOrElse(Timeout(60.seconds))

    val listenerTimeout =
      getTimeout("listener-timeout")
        .getOrElse(Timeout(10.seconds))

    startServiceInfra(boot, overallTimeout, listenerTimeout)
  }

  def startServiceInfra(
    boot: UnicomplexBoot,
    timeout: Timeout,
    listenerTimeout: Timeout
  )(implicit actorSystem: ActorSystem): Unit = {
    import actorSystem.dispatcher
    val startTime = System.nanoTime
    implicit val to = listenerTimeout
    val ackFutures =
      for ((listenerName, config) <- boot.listeners) yield {
        val responseFuture = Unicomplex(actorSystem).uniActor ? StartListener(listenerName, config)
        responseFuture.onComplete {
          case Failure(t) if (t.isInstanceOf[TimeoutException]) =>
            logger.error(s"The Unicomplex could not start the listener, $listenerName, within $to.", t)
          case Failure(t) =>
            logger.error(s"The Unicomplex failed to start the listener, $listenerName.", t)
          case Success(StartFailure(t)) =>
            logger.error(s"The Unicomplex reported a start failure for the listener, $listenerName.", t)
          case _ =>
        }
        responseFuture
      }

    // Block for the web service to be started.
    Await.ready(Future.sequence(ackFutures), timeout.duration)

    val elapsed = (System.nanoTime - startTime) / 1000000
    logger.info(s"Web Service started in $elapsed milliseconds")
  }

  @tailrec
  private[unicomplex] def getRootCause(e: Throwable): Throwable = {
    Option(e.getCause) match {
      case Some(ex) => getRootCause(ex)
      case None => e
    }
  }
}

case class UnicomplexBoot private[unicomplex](startTime: Timestamp,
                                              addOnConfig: Option[Config] = None,
                                              config: Config,
                                              actorSystemCreator: (String, Config) => ActorSystem = { (name, config) => ActorSystem(name, config) },
                                              cubes: Seq[CubeInit] = Seq.empty,
                                              listeners: Map[String, Config] = Map.empty,
                                              listenerAliases: Map[String, String] = Map.empty,
                                              jarConfigs: Seq[(String, Config)] = Seq.empty,
                                              jarNames: Seq[String] = Seq.empty,
                                              actors: Seq[(String, String, String, Class[_])] = Seq.empty,
                                              extensions: Seq[SqubsExtension] = Seq.empty,
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
    UnicomplexBoot.scanResources(resources map (new File(_).toURI.toURL))(this)

  def scanResources(resources: java.util.List[String]): UnicomplexBoot = scanResources(resources.asScala.toSeq: _*)

  def scanResources(withClassPath: Boolean, resources: Array[String]): UnicomplexBoot =
    scanResources(withClassPath, resources.toIndexedSeq: _*)

  def initExtensions: UnicomplexBoot = {

    val initSeq = cubes.flatMap { cube =>
      cube.components.getOrElse(StartupType.EXTENSIONS, Seq.empty) map { config =>
        val className = config getString "class-name"
        val seqNo = config.get[Int]("sequence", Int.MaxValue)
        (seqNo, className, cube)
      }
    }.sortBy(_._1)

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

  def start(implicit timeout: Timeout): UnicomplexBoot = synchronized {

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
    actorSystem.registerOnTermination {
      UnicomplexBoot.actorSystems -= actorSystem.name
    }

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

    val preCubesInitExtensions = extensions map extensionOp("preCubesInit", _.preCubesInit())
    uniActor ! Extensions(preCubesInitExtensions)

    // Start all actors
    val (futures, actorsUnflat) = cubes.map(startComponents(_, listenerAliases)).unzip
    val actors = actorsUnflat.flatten

    import actorSystem.dispatcher
    Await.ready(Future.sequence(futures), timeout.duration)

    // Start the service infrastructure if services are enabled and registered.
    if (startServices) startServiceInfra(this)

    val postInitExtensions = preCubesInitExtensions map extensionOp("postInit", _.postInit())

    // Update the extension errors in Unicomplex actor, in case there are errors.
    uniActor ! Extensions(postInitExtensions)

    {
      // Tell Unicomplex we're done.
      val stateFuture = Unicomplex(actorSystem).uniActor ? Activate
      Try(Await.result(stateFuture, timeout.duration)) recoverWith { case _: TimeoutException =>
        val recoverFuture = Unicomplex(actorSystem).uniActor ? ActivateTimedOut
        Try(Await.result(recoverFuture, timeout.duration))
      } match {
        case Success(Active) => logger.info(s"[$actorSystemName] activated")
        case Success(Failed) => logger.info(s"[$actorSystemName] initialization failed.")
        case e => logger.warn(s"[$actorSystemName] awaiting confirmation, $e.")
      }
    }

    val boot = copy(config = actorSystem.settings.config, actors = actors, extensions = postInitExtensions, started = true)
    Unicomplex(actorSystem).boot.set(boot)
    boot
  }

  def registerExtensionShutdown(actorSystem: ActorSystem): Unit = {
    if (extensions.nonEmpty) {
      actorSystem.registerOnTermination {
        // Run the shutdown in a different thread, not in the ActorSystem's onTermination thread.
        import scala.concurrent.Future

        // Kill the JVM if the shutdown takes longer than the timeout.
        if (stopJVM) {
          val shutdownTimer = new Timer(true)
          shutdownTimer.schedule(new TimerTask {
            def run(): Unit = {
              System.exit(0)
            }
          }, 5000)
        }

        // Then run the shutdown in the global execution context.
        import scala.concurrent.ExecutionContext.Implicits.global
        Future {
          extensions.reverse foreach { e =>
            import e.info._
            e.extLifecycle foreach { elc =>
              logger.info(s"Shutting down extension ${elc.getClass.getName} in $fullName $version")
              elc.shutdown()
            }
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

  def loadExtension(seqNo: Int, className: String, cube: CubeInit): SqubsExtension = {
    try {
      val clazz = Class.forName(className, true, getClass.getClassLoader)
      val extLifecycle = ExtensionLifecycle(this) { clazz.asSubclass(classOf[ExtensionLifecycle]).newInstance }
      SqubsExtension(cube.info, seqNo, Some(extLifecycle), Seq.empty)
    } catch {
      case NonFatal(e) =>
        import cube.info._
        val t = getRootCause(e)
        logger.warn(s"Can't load extension $className.\n" +
          s"Cube: $fullName $version\n" +
          s"Path: $jarPath\n" +
          s"${t.getClass.getName}: ${t.getMessage}")
        t.printStackTrace()
        SqubsExtension(cube.info, seqNo, None, Seq("load" -> t))
    }
  }

  def extensionOp(opName: String, opFn: ExtensionLifecycle => Unit)
                 (extension: SqubsExtension): SqubsExtension = {
    import extension.info._

    extension.extLifecycle match {
      case None => extension
      case Some(l) =>
        try {
          opFn(l)
          logger.info(s"Success $opName extension ${l.getClass.getName} in $fullName $version")
          extension
        } catch {
          case NonFatal(e) =>
            val t = getRootCause(e)
            logger.warn(s"Error on $opName extension ${l.getClass.getName}\n" +
              s"Cube: $fullName $version\n" +
              s"${t.getClass.getName}: ${t.getMessage}")
            t.printStackTrace()
            extension.copy(exceptions = extension.exceptions :+ (opName -> t))
        }
    }
  }
}

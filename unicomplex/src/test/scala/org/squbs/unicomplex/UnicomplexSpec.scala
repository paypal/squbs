package org.squbs.unicomplex

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}
import java.io.{FileNotFoundException, File}
import scala.concurrent.duration._
import org.scalatest.concurrent.AsyncAssertions
import scala.io.Source
import scala.concurrent._
import akka.pattern.ask
import akka.actor.{ActorSystem, ActorRef}
import scala.util.{Success, Failure, Try}
import org.squbs.unicomplex.dummyextensions.DummyExtension
import org.squbs.lifecycle.GracefulStop
import com.typesafe.config.ConfigFactory

/**
 * Created by zhuwang on 2/21/14.
 */
object UnicomplexSpec {

  val dummyJarsDir = new File("unicomplex/src/test/resources/classpaths")

  val classPaths =
    if (dummyJarsDir.exists && dummyJarsDir.isDirectory) {
      dummyJarsDir.listFiles().map(_.getAbsolutePath)
    } else {
      throw new RuntimeException("[UnicomplexSpec] There is no cube to be loaded")
    }

  import collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "initTest",
      "squbs." + JMX.prefixConfig -> Boolean.box(true)
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class UnicomplexSpec extends TestKit(UnicomplexSpec.boot.actorSystem) with ImplicitSender
                             with WordSpecLike with Matchers with BeforeAndAfterAll
                             with AsyncAssertions {

  import UnicomplexSpec._

  implicit val timeout: akka.util.Timeout = 2.seconds

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

  sys.addShutdownHook {
    system.shutdown()
  }

  override def beforeAll() {
    def svcReady = Try {
      Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").getLines()
    } match {
      case Success(_) => true
      case Failure(e) => println(e.getMessage); false
    }

    var retry = 5
    while (!svcReady && retry > 0) {
      Thread.sleep(1000)
      retry -= 1
    }

    if (retry == 0) throw new Exception("Starting service timeout in 5s")
  }
  
  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "UnicomplexBoot" must {

    "start all cube actors" in {
      val w = new Waiter

      system.actorSelection("/user/DummyCube").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      w.await()

      system.actorSelection("/user/DummyCube/Appender").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      w.await()

      system.actorSelection("/user/DummyCube/Prepender").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      w.await()

      system.actorSelection("/user/DummyCubeSvc/PingPongPlayer").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      w.await()
    }

    "start all services" in {
      assert(boot.services.size == 2)

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "preInit, init and postInit all extenstions" in {
      assert(boot.extensions.size == 2)

      assert(boot.extensions.forall(_._3.isInstanceOf[DummyExtension]))
      assert(boot.extensions(0)._3.asInstanceOf[DummyExtension].state == "AstartpreInitinitpostInit")
      assert(boot.extensions(1)._3.asInstanceOf[DummyExtension].state == "BstartpreInitinitpostInit")
    }
  }

  "CubeSupervisor" must {

    "get init reports from cube actors" in {
      val w = new Waiter

      var supervisor: ActorRef  = null
      system.actorSelection("/user/CubeA").resolveOne().onComplete(result => {
        w {
          assert(result.isSuccess)
          supervisor = result.get
        }
        w.dismiss()
      })
      w.await()

      val statusFuture = supervisor ? CheckInitStatus

      statusFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.isInstanceOf[(InitReports, Boolean)])
          val report = result.get.asInstanceOf[(InitReports, Boolean)]._1
          assert(report.state == Active)
          assert(report.reports.size == 2)
        }
        w.dismiss()
      })
      w.await()
    }

    "get init reports from cube actors even if the actor failed in init" in {
      val w = new Waiter

      var supervisor: ActorRef  = null
      system.actorSelection("/user/InitFail").resolveOne().onComplete(result => {
        w {
          assert(result.isSuccess)
          supervisor = result.get
        }
        w.dismiss()
      })
      w.await()

      val statusFuture = supervisor ? CheckInitStatus

      statusFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.isInstanceOf[(InitReports, Boolean)])
          val report = result.get.asInstanceOf[(InitReports, Boolean)]._1
          assert(report.state == Failed)
          assert(report.reports.size == 1)
        }
        w.dismiss()
      })
      w.await()
    }
  }

  "deal with the situation that cube actors are not able to send the reports" in {
    val w = new Waiter

    var supervisor: ActorRef  = null
    system.actorSelection("/user/InitBlock").resolveOne().onComplete(result => {
      w {
        assert(result.isSuccess)
        supervisor = result.get
      }
      w.dismiss()
    })
    w.await()

    val statusFuture = supervisor ? CheckInitStatus

    statusFuture.onComplete(result => {
      w {
        assert(result.isSuccess)
        assert(result.get.isInstanceOf[(InitReports, Boolean)])
        val report = result.get.asInstanceOf[(InitReports, Boolean)]._1
        assert(report.state == Initializing)
        assert(report.reports.size == 1)
      }
      w.dismiss()
    })
    w.await()
  }

  "UniComplex" must {

    "get cube init reports" in {
      val w = new Waiter

      val statusFuture = Unicomplex(system).uniActor ? ReportStatus

      statusFuture.onComplete(report => {
        w {
          assert(report.isSuccess)
          assert(report.get.isInstanceOf[(LifecycleState, Map[ActorRef, (CubeRegistration, Option[InitReports])])])
          val (systemState, cubes) = report.get.asInstanceOf[(LifecycleState, Map[ActorRef, (CubeRegistration, Option[InitReports])])]
          assert(systemState == Failed)
          val cubeAReport = cubes.values.find(_._1.name == "CubeA").flatMap(_._2)
          assert(cubeAReport != None)
          assert(cubeAReport.get.state == Active)
          val cubeBReport = cubes.values.find(_._1.name == "CubeB").flatMap(_._2)
          assert(cubeBReport != None)
          assert(cubeBReport.get.state == Active)
          val initFailReport = cubes.values.find(_._1.name == "InitFail").flatMap(_._2)
          assert(initFailReport != None)
          assert(initFailReport.get.state == Failed)
          val initBlockReport = cubes.values.find(_._1.name == "InitBlock").flatMap(_._2)
          assert(initBlockReport != None)
          assert(initBlockReport.get.state == Initializing)
        }
        w.dismiss()
      })
      w.await()
    }

    "stop a single cube without affect other cubes" in {

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")

      val w = new Waiter

      val stopCubeFuture = Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")

      stopCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
        println("Cube stop acknowledged.")
      })

      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isFailure)}
        w.dismiss()
      })

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      w.await()

    }

    "not mess up if stop a stopped cube" in {
      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      val w = new Waiter

      val stopCubeFuture = Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")

      stopCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
      })

      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isFailure)}
        w.dismiss()
      })

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      w.await()
    }

    "start a single cube correctly" in {
      val w = new Waiter

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      val startCubeFuture =
        Unicomplex(system).uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc",
          boot.initInfoMap, boot.listenerAliases)

      startCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
      })

      w.await()

      Thread.sleep(1000)

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if start a running cube" in {
      val w = new Waiter

      val startCubeFuture =
        Unicomplex(system).uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc", boot.initInfoMap,
          boot.listenerAliases)

      startCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
      })

      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if stop and start a cube contains actors and services simultaneously" in {
      val stopCubeFuture = Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")
      val startCubeFuture =
        Unicomplex(system).uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc", boot.initInfoMap,
          boot.listenerAliases)

      val w = new Waiter
      Future.sequence(Seq(stopCubeFuture, startCubeFuture)).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      w.await()

      def svcReady = Try {
        assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

        assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
        assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
      } match {
        case Success(_) => true
        case Failure(e) => println(e.getMessage); false
      }

      var retry = 5
      while (!svcReady && retry > 0) {
        Thread.sleep(1000)
        retry -= 1
      }

      if (retry == 0) throw new Exception("service timeout in 5s")

    }

    "not mess up if stop and start a cube contains actors only simultaneously" in {
      val stopCubeFuture = Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummyCube")
      val startCubeFuture = Unicomplex(system).uniActor ?
        StartCube("org.squbs.unicomplex.test.DummyCube", boot.initInfoMap,
          boot.listenerAliases)

      val w = new Waiter
      Future.sequence(Seq(stopCubeFuture, startCubeFuture)).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      w.await()

      def svcReady = Try {
        assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")
        assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
        assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
      } match {
        case Success(_) => true
        case Failure(e) => println(e.getMessage); false
      }

      var retry = 5
      while (!svcReady && retry > 0) {
        Thread.sleep(1000)
        retry -= 1
      }

      if (retry == 0) throw new Exception("service timeout in 5s")
    }

    "not mess up if stop and start a cube contains services only simultaneously" in {
      val stopCubeFuture = Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummySvc")
      val startCubeFuture =
        Unicomplex(system).uniActor ? StartCube("org.squbs.unicomplex.test.DummySvc", boot.initInfoMap,
          boot.listenerAliases)

      val w = new Waiter
      Future.sequence(Seq(stopCubeFuture, startCubeFuture)).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if stop all cubes simultaneously" in {
      val w = new Waiter

      Future.sequence(Seq(
        Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummySvc"),
        Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummyCube"),
        Unicomplex(system).uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")
      )).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }
    }

    "not mess up if start all cubes simultaneously" in {
      val w = new Waiter

      Future.sequence(Seq(
        Unicomplex(system).uniActor ? StartCube("org.squbs.unicomplex.test.DummySvc", boot.initInfoMap,
          boot.listenerAliases),
        Unicomplex(system).uniActor ? StartCube("org.squbs.unicomplex.test.DummyCube", boot.initInfoMap,
          boot.listenerAliases),
        Unicomplex(system).uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc", boot.initInfoMap,
          boot.listenerAliases)
      )).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      def svcReady = Try {
        assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")
        assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
        assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
      } match {
        case Success(_) => true
        case Failure(e) => println(e.getMessage); false
      }

      var retry = 5
      while (!svcReady && retry > 0) {
        Thread.sleep(1000)
        retry -= 1
      }

      if (retry == 0) throw new Exception("service timeout in 5s")
    }
  }
}

package org.squbs.unicomplex

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._
import scala.concurrent.duration._
import org.scalatest.concurrent.AsyncAssertions
import scala.io.Source
import akka.actor.ActorSystem
import scala.util.Try
import org.squbs.unicomplex.dummyextensions.DummyExtension
import com.typesafe.config.ConfigFactory
import scala.util.Failure
import scala.util.Success
import org.squbs.lifecycle.GracefulStop

/**
 * Created by zhuwang on 2/21/14.
 */
object UnicomplexSpec {

  val dummyJarsDir = "unicomplex/src/test/resources/classpaths"

  val classPaths = Array(
    "DummyCube",
    "DummyCubeSvc",
    "DummySvc",
    "DummyExtensions.jar"
  ) map (dummyJarsDir + "/" + _)

  import collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "unicomplexSpec",
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

  override def beforeAll() {
    def svcReady = Try {
      Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").getLines()
      Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").getLines()
      Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").getLines()
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
}

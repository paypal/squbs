package org.squbs.unicomplex

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import javax.management.ObjectName
import akka.actor.ActorSystem
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.UnicomplexBoot.StartupType
import org.squbs.unicomplex.dummyextensions.DummyExtension
import spray.can.Http
import spray.http._
import scala.concurrent.duration._
import scala.util.Try

/**
 * Created by zhuwang on 2/21/14.
 */
object UnicomplexSpec {

  val dummyJarsDir = "unicomplex/src/test/resources/classpaths"

  val classPaths = Array(
    "DummyCube",
    "DummyCubeSvc",
    "DummySvc",
    "DummySvcActor",
    "DummyExtensions.jar"
  ) map (dummyJarsDir + "/" + _)

  import scala.collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "unicomplexSpec",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-port" -> org.squbs.nextPort.toString
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

  import org.squbs.unicomplex.UnicomplexSpec._

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher
  
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
      val services = boot.cubes flatMap { cube => cube.components.getOrElse(StartupType.SERVICES, Seq.empty) }
      assert(services.size == 3)
      (IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/dummysvc/msg/hello")))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("^hello$")
      }

      (IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pingpongsvc/ping")))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("Pong")
      }

      (IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/pingpongsvc/pong")))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("Ping")
      }

      (IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/dummysvcactor/ping")))
      within(timeout.duration) {
        val response = expectMsgType[HttpResponse]
        response.status should be(StatusCodes.OK)
        response.entity.asString should be("pong")
      }
    }

    "check cube MXbean" in {
      import JMX._
      val mbeanServer = ManagementFactory.getPlatformMBeanServer
      val cubesObjName = new ObjectName(prefix(system) + cubesName)
      val attr = mbeanServer.getAttribute(cubesObjName, "Cubes")
      attr shouldBe a [Array[javax.management.openmbean.CompositeData]]
      // 5 cubes registered above.
      attr.asInstanceOf[Array[javax.management.openmbean.CompositeData]] should have size 5
    }

    "check cube state MXbean" in {
      import JMX._
      val cubeName = "DummyCube"
      val mbeanServer = ManagementFactory.getPlatformMBeanServer
      val cubesObjName = new ObjectName(prefix(system) + cubeStateName + cubeName)
      val name = mbeanServer.getAttribute(cubesObjName, "Name")
      val cubeState = mbeanServer.getAttribute(cubesObjName, "CubeState")
      name should be (cubeName)
      cubeState should be ("Active")
    }

    "check listener MXbean" in {
      import JMX._
      val mbeanServer = ManagementFactory.getPlatformMBeanServer
      val listenersObjName = new ObjectName(prefix(system) + listenersName)
      val listeners = mbeanServer.getAttribute(listenersObjName, "Listeners")
      listeners shouldBe a [Array[javax.management.openmbean.CompositeData]]
      // 3 services registered on one listener
      listeners.asInstanceOf[Array[javax.management.openmbean.CompositeData]] should have size 3

    }

    "preInit, init and postInit all extenstions" in {
      boot.extensions.size should be (2)
      boot.extensions.forall(_._3.isInstanceOf[DummyExtension]) should be (true)
      boot.extensions(0)._3.asInstanceOf[DummyExtension].state should be ("AstartpreInitinitpostInit")
      boot.extensions(1)._3.asInstanceOf[DummyExtension].state should be ("BstartpreInitinitpostInit")
    }
  }
}

package org.squbs.unicomplex

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.AsyncAssertions
import org.squbs._
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.dummysvcactor.RegisterTimeoutHandler
import spray.can.Http
import spray.http._

import scala.concurrent.duration._
import scala.util.Try

object UnicomplexTimeoutSpec {

  val dummyJarsDir = "squbs-unicomplex/src/test/resources/classpaths"

  val classPaths = Array(
    "DummySvcActor"
  ) map (dummyJarsDir + "/" + _)

  val aConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = unicomplexTimeoutSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener {
       |  bind-port = $nextPort
       |}
       |spray.can.server {
       |  request-timeout = 5s
       |}
     """.stripMargin)

  val boot = UnicomplexBoot(aConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}

class UnicomplexTimeoutSpec extends TestKit(UnicomplexTimeoutSpec.boot.actorSystem) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll
with AsyncAssertions {

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (10 seconds)

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Unicomplex" must {

    "Cause a timeout event" in {
      system.settings.config getString "spray.can.server.request-timeout" should be ("5s")
      system.actorSelection("/user/DummySvcActor/dummysvcactor-handler") ! RegisterTimeoutHandler
      val path = "/dummysvcactor/timeout"
      (IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port$path")))
      within(timeout.duration) {
        val timedOut = expectMsgType[Timedout]
        timedOut.request match {
          case req: HttpRequest =>
            req.uri.path.toString() should be (path)
          case x =>
            println(s"Received unexpected TimedOut with type ${x.getClass.getName}")
            assert(false)
        }
      }
    }
  }
}

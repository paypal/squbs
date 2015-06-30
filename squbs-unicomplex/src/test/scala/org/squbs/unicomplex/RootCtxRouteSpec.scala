package org.squbs.unicomplex

import akka.actor.ActorSystem
import akka.io.IO
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import spray.can.Http
import spray.http._
import spray.routing.Directives._
import spray.routing.Route
import spray.util.Utils

import scala.concurrent.duration._

object RootCtxRouteSpec{
  /*
  cube-name = org.squbs.unicomplex.test.RootRoute
  cube-version = "0.0.1"
  squbs-services = [
      {
          class-name = org.squbs.unicomplex.RootRoute
          web-context = ""
      }
  ]
   */
  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/RootCtxRoute").getPath)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = $port
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class RootCtxRouteSpec extends TestKit(
  RootCtxRouteSpec.boot.actorSystem) with FlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  implicit val timeout: akka.util.Timeout = 10 seconds

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "Route" should "handle request with empty web-context" in {
    IO(Http) ! HttpRequest(HttpMethods.GET, Uri(s"http://127.0.0.1:$port/ping"))
    within(timeout.duration) { 
      val response = expectMsgType[HttpResponse]
      response.status should be(StatusCodes.OK)
      response.entity.asString should be("pong")
    }
  }
}

class RootRoute extends RouteDefinition {
  override def route: Route = path("ping") {
    complete{"pong"}
  }
}

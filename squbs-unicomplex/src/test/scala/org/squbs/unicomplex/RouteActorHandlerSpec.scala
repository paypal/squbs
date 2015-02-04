package org.squbs.unicomplex

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import spray.can.Http
import spray.client.pipelining._
import spray.routing._
import spray.util.Utils

import scala.concurrent.Await

class RouteActorHandlerSpec
  extends TestKit(ActorSystem())
  with FlatSpecLike
  with BeforeAndAfterAll
  with Matchers {

  import akka.pattern.ask

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  override protected def afterAll(): Unit = {
    system.shutdown()
    super.afterAll()
  }

  val (interface, port) = Utils.temporaryServerHostnameAndPort("localhost")
  val service = system.actorOf(Props(classOf[RouteActor], "ctx", classOf[Service]))

  val timeoutDuration = 1 minute

  implicit val timeout = Timeout(timeoutDuration)

  Await.result(IO(Http) ? Http.Bind(service, interface = interface, port = port), timeoutDuration)

  "Rejection handler" should "be applied to the route actor" in {
    val pipeline = sendReceive
    val response = Await.result(pipeline(Get(s"http://$interface:$port/ctx/reject")), 1 minute)
    response.entity.asString should be("rejected")
  }

  "Exception handler" should "be applied to the route actor" in {
    val pipeline = sendReceive
    val response = Await.result(pipeline(Get(s"http://$interface:$port/ctx/exception")), 1 minute)
    response.entity.asString should be("exception")
  }
}

class Service extends RouteDefinition with Directives {

  override def rejectionHandler: Option[RejectionHandler] = Some(RejectionHandler {
    case ServiceRejection :: _ => complete("rejected")
  })

  override def exceptionHandler: Option[ExceptionHandler] = Some(ExceptionHandler {
    case ServiceException => complete("exception")
  })

  override def route: Route = path("reject") {
    reject(ServiceRejection)
  } ~ path("exception") {
    ctx =>
      throw ServiceException
  }

  object ServiceRejection extends Rejection

  object ServiceException extends Exception

}
package org.squbs.unicomplex

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.{Matchers, BeforeAndAfterAll, FlatSpecLike}
import spray.can.Http
import spray.routing._

import scala.concurrent.Await
import scala.util.Random
import spray.http._
import spray.client.pipelining._

class RouteActorHandlerSpec
  extends TestKit(ActorSystem())
  with FlatSpecLike
  with BeforeAndAfterAll
  with Matchers {

  import akka.pattern.ask

  import scala.concurrent.duration._

  import scala.concurrent.ExecutionContext.Implicits.global

  override protected def afterAll(): Unit = {
    system.shutdown()
    super.afterAll()
  }

  val port = new Random(System.currentTimeMillis()).nextInt(1000) + 9000

  val service = system.actorOf(Props(classOf[RouteActor], "ctx", classOf[Service]))

  val timeoutDuration = 15 seconds
  implicit val timeout = Timeout(timeoutDuration)

  Await.result(IO(Http) ? Http.Bind(service, interface = "localhost", port = port), timeoutDuration)

  "Rejection handler" should "be applied to the route actor" in {
    val pipeline = sendReceive
    val response = Await.result(pipeline(Get(s"http://localhost:$port/ctx/reject")), 5 seconds)
    response.entity.asString should be("rejected")
  }

  "Exception handler" should "be applied to the route actor" in {
    val pipeline = sendReceive
    val response = Await.result(pipeline(Get(s"http://localhost:$port/ctx/exception")), 5 seconds)
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
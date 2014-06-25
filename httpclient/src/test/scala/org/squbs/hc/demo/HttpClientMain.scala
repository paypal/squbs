package org.squbs.hc.demo

import akka.actor.{Props, Actor, ActorSystem}
import org.squbs.hc.routing.{RoutingRegistry, RoutingDefinition}
import akka.io.IO
import spray.can.Http
import akka.pattern._
import scala.concurrent.duration._
import spray.util._
import scala.util.{Failure, Success}
import spray.http.{HttpMethods, StatusCodes}
import org.squbs.hc.{HttpResponseEntityWrapper, HttpResponseWrapper, HttpClient}
import org.squbs.hc.actor.{HttpClientCallActor, HttpClientManager}
import org.squbs.hc.actor.HttpClientMessage.{HttpClientGetMsg, HttpClientPostMsg}
import scala.concurrent.Future
import akka.util.Timeout
import org.squbs.hc.config.{Configuration, HostConfiguration, ServiceConfiguration}
import akka.actor.Actor.Receive

/**
 * Created by hakuang on 6/13/2014.
 */
object HttpClientMain1 extends App {

  private implicit val system = ActorSystem("HttpClientMain1")
  import system.dispatcher
  RoutingRegistry.register(new GoogleMapAPIRoutingDefinition)

  val response = HttpClient.create("googlemap").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
  response onComplete {
    case Success(HttpResponseWrapper(StatusCodes.OK, Right(res))) =>
      println("Success, response entity is: " + res.entity.asString)
      shutdown
    case Success(HttpResponseWrapper(code, _)) =>
      println("Success, the status code is: " + code)
      shutdown
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdown
  }

  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}

object HttpClientMain2 extends App {

  private implicit val system = ActorSystem("HttpClientMain2")
  import system.dispatcher
  RoutingRegistry.register(new GoogleMapAPIRoutingDefinition)
  import org.squbs.hc.json.Json4sJacksonNoTypeHintsProtocol._

  val response = HttpClient.create("googlemap").getEntity[GoogleApiResult[Elevation]]("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
  response onComplete {
    case Success(HttpResponseEntityWrapper(StatusCodes.OK, Right(res), rawHttpResponse)) =>
      println("Success, response status is: " + res.status + ", elevation is: " + res.results.head.elevation + ", location.lat is: " + res.results.head.location.lat)
      shutdown
    case Success(HttpResponseEntityWrapper(code, _, _)) =>
      println("Success, the status code is: " + code)
      shutdown
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdown
  }

  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}

object HttpClientMain3 extends App {

  private implicit val system = ActorSystem("HttpClientMain3")
  import system.dispatcher
  RoutingRegistry.register(new GoogleMapAPIRoutingDefinition)

  implicit val timeout: Timeout = 2 seconds
  val response = HttpClientManager(system).httpClientActor ? HttpClientGetMsg("googlemap", "/api/elevation/json?locations=27.988056,86.925278&sensor=false", HttpMethods.GET, None, None, None)
  response onComplete {
    case Success(HttpResponseWrapper(StatusCodes.OK, Right(res))) =>
      println("Success, response entity is: " + res.entity.asString)
      shutdown
    case Success(HttpResponseWrapper(code, _)) =>
      println("Success, the status code is: " + code)
      shutdown
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdown
  }

  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}

object HttpClientMain5 extends App {

  private implicit val system = ActorSystem("HttpClientMain5")
  import system.dispatcher
  RoutingRegistry.register(new GoogleMapAPIRoutingDefinition)
  import org.squbs.hc.json.Json4sJacksonNoTypeHintsProtocol._

  implicit val timeout:Timeout = 2 seconds
  val response = HttpClientManager(system).httpClientActor ? HttpClientGetMsg("googlemap", "/api/elevation/json?locations=27.988056,86.925278&sensor=false", HttpMethods.GET, None, None, None)
  response onComplete {
    case Success(HttpResponseWrapper(StatusCodes.OK, Right(res))) =>
      val obj = HttpClientManager.unmarshal[GoogleApiResult[Elevation]](res)
      println("Success, response status is: " + res.status + ", elevation is: " + obj.get.results.head.elevation + ", location.lat is: " + obj.get.results.head.location.lat)
      shutdown
    case Success(HttpResponseWrapper(code, _)) =>
      println("Success, the status code is: " + code)
      shutdown
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdown
  }

  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}

object HttpClientMain4 extends App {

  private implicit val system = ActorSystem("HttpClientMain4")
  import system.dispatcher
  RoutingRegistry.register(new GoogleMapAPIRoutingDefinition)
  import org.squbs.hc.json.Json4sJacksonNoTypeHintsProtocol._

  implicit val timeout: Timeout = 2 seconds
  val googleMapAPIActor = system.actorOf(Props(new GoogleMapAPIActor()))
  googleMapAPIActor ! GoogleMapAPIMessage
}

case object GoogleMapAPIMessage

class GoogleMapAPIActor(implicit system: ActorSystem) extends Actor {
  override def receive: Receive = {
    case GoogleMapAPIMessage =>
      import org.squbs.hc.json.Json4sJacksonNoTypeHintsProtocol._
      HttpClientManager(system).httpClientActor ! HttpClientGetMsg("googlemap", "/api/elevation/json?locations=27.988056,86.925278&sensor=false", HttpMethods.GET, None, None, None)
    case HttpResponseWrapper(StatusCodes.OK, Right(res)) =>
      println("Success, response entity is: " + res.entity.asString)
      shutdown()
    case HttpResponseWrapper(code, _) =>
      shutdown()
    case e: Throwable =>
      println("Failure, the reason is: " + e.getMessage)
      shutdown()
  }
  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}

class GoogleMapAPIRoutingDefinition extends RoutingDefinition {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      Some("http://maps.googleapis.com/maps")
    else
      None
  }

  override def name: String = "googlemap"
}

case class Elevation(location: Location, elevation: Double)
case class Location(lat: Double, lng: Double)
case class GoogleApiResult[T](status: String, results: List[T])

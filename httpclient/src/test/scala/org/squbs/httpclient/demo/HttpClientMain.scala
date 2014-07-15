package org.squbs.httpclient.demo

import akka.actor.{ActorRef, Props, Actor, ActorSystem}
import org.squbs.httpclient.endpoint.{EndpointRegistry, EndpointResolver}
import akka.io.IO
import spray.can.Http
import akka.pattern._
import scala.concurrent.duration._
import spray.util._
import spray.http.{StatusCodes}
import org.squbs.httpclient._
import org.squbs.httpclient.actor.{HttpClientManager}
import akka.util.Timeout
import scala.util.Failure
import scala.Some
import org.squbs.httpclient.HttpResponseEntityWrapper
import scala.util.Success
import org.squbs.httpclient.HttpResponseWrapper
import org.squbs.httpclient.actor.HttpClientManagerMessage.CreateHttpClient
import org.squbs.httpclient.actor.HttpClientActorMessage.Get

/**
 * Created by hakuang on 6/13/2014.
 */

/**
 * Traditional API using get
 */
object HttpClientMain1 extends App {

  private implicit val system = ActorSystem("HttpClientMain1")
  import system.dispatcher
  EndpointRegistry.register(new GoogleMapAPIEndpointResolver)

  val response = HttpClientFactory.getOrCreate("googlemap").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
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

/**
 * Traditional API using getEntity
 */
object HttpClientMain2 extends App {

  private implicit val system = ActorSystem("HttpClientMain2")
  import system.dispatcher
  EndpointRegistry.register(new GoogleMapAPIEndpointResolver)
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  val response = HttpClientFactory.getOrCreate("googlemap").getEntity[GoogleApiResult[Elevation]]("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
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

/**
 * Message Based API:Get using ask
 */
object HttpClientMain3 extends App {

  private implicit val system = ActorSystem("HttpClientMain3")
  import system.dispatcher
  EndpointRegistry.register(new GoogleMapAPIEndpointResolver)

  implicit val timeout: Timeout = 2 seconds
  val createResponse = HttpClientManager(system).httpClientManager ? CreateHttpClient(name = "googlemap")
  createResponse onComplete {
    case Success(httpClientActor: ActorRef) =>
      println(s"Success, creating HttpClient")
      val response = httpClientActor ? Get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
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
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdown
  }

  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}

/**
 * Message Based API:GetEntity using ask
 */
object HttpClientMain4 extends App {

  private implicit val system = ActorSystem("HttpClientMain4")
  import system.dispatcher
  EndpointRegistry.register(new GoogleMapAPIEndpointResolver)
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  implicit val timeout:Timeout = 2 seconds

  val createResponse = HttpClientManager(system).httpClientManager ? CreateHttpClient(name = "googlemap")
  createResponse onComplete {
    case Success(httpClientActor: ActorRef) =>
      println(s"Success, creating HttpClient")
      val response = httpClientActor ? Get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
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
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdown
  }

  def shutdown() {
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }
}

/**
 * Message Based API:Get using tell
 */
object HttpClientMain5 extends App {

  private implicit val system = ActorSystem("HttpClientMain5")
  import system.dispatcher
  EndpointRegistry.register(new GoogleMapAPIEndpointResolver)
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  implicit val timeout: Timeout = 2 seconds
  val googleMapAPIActor = system.actorOf(Props(new GoogleMapAPIActor5()))
  googleMapAPIActor ! GoogleMapAPIMessage
}

case object GoogleMapAPIMessage

class GoogleMapAPIActor5(implicit system: ActorSystem) extends Actor {
  override def receive: Receive = {
    case GoogleMapAPIMessage =>
      HttpClientManager(system).httpClientManager ! CreateHttpClient(name = "googlemap")
    case httpClientActor: ActorRef =>
      import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._
      httpClientActor ! Get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    case HttpResponseWrapper(StatusCodes.OK, Right(res)) =>
      println("Success, response entity is: " + res.entity.asString)
      shutdown
    case HttpResponseWrapper(code, _) =>
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

class GoogleMapAPIEndpointResolver extends EndpointResolver {
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
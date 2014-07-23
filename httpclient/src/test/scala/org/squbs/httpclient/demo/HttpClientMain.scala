package org.squbs.httpclient.demo

import akka.actor.ActorSystem
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry, EndpointResolver}
import akka.io.IO
import spray.can.Http
import akka.pattern._
import scala.concurrent.duration._
import spray.util._
import spray.http.{StatusCodes}
import org.squbs.httpclient._
import scala.util.Failure
import scala.Some
import org.squbs.httpclient.HttpResponseEntityWrapper
import scala.util.Success
import org.squbs.httpclient.HttpResponseWrapper
import org.squbs.httpclient.env.{Environment, Default}

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

class GoogleMapAPIEndpointResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    if (svcName == name)
      Some(Endpoint("http://maps.googleapis.com/maps"))
    else
      None
  }

  override def name: String = "googlemap"
}

case class Elevation(location: Location, elevation: Double)
case class Location(lat: Double, lng: Double)
case class GoogleApiResult[T](status: String, results: List[T])
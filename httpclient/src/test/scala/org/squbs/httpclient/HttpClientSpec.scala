package org.squbs.httpclient

import org.squbs.httpclient.endpoint.{EndpointRegistry, EndpointResolver}
import spray.http.StatusCodes
import spray.can.Http
import akka.io.IO
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import spray.util._
import org.scalatest._
import scala.concurrent.Await
import spray.can.Http.ConnectionAttemptFailedException
import scala.util.Success
import scala.util.Failure
import scala.Some
import org.squbs.httpclient.config.{HostConfiguration, ServiceConfiguration, Configuration}


case class Elevation(location: Location, elevation: Double)
case class Location(lat: Double, lng: Double)
case class GoogleApiResult[T](status: String, results: List[T])


/**
 * Created by hakuang on 5/12/2014.
 */
class HttpClientSpec extends FlatSpec with Matchers with BeforeAndAfterAll{

  private implicit val system = ActorSystem("HttpClientSpec")
  import system.dispatcher
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  override def beforeAll {
    EndpointRegistry.register(new GoogleMapEndpointResolver())
    EndpointRegistry.register(new GoogleMapHttpsEndpointResolver())
    EndpointRegistry.register(new GoogleMap2EndpointResolverMarkdown())
    EndpointRegistry.register(new GoogleMapNotExistingEndpointResolver())
  }

  override def afterAll {
    EndpointRegistry.endpointResolvers.clear
    HttpClientFactory.httpClientMap.clear
    IO(Http).ask(Http.CloseAll)(1.second).await
    system.shutdown()
  }

  "HttpClient.create('googlemap').get(uri) with correct endpoint" should "get StatusCodes.OK" in {
    val response = HttpClientFactory.getOrCreate("googlemap").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
  }

  "User could use third party endpoint as the service name directly" should "get StatusCodes.OK" in {
    val response = HttpClientFactory.getOrCreate("http://maps.googleapis.com/maps").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
  }

  "Update HttpClient" should "get the updated value" in {
    val httpClient = HttpClientFactory.getOrCreate("googlemap")
    httpClient.name should be ("googlemap")
    httpClient.env should be (None)
    httpClient.config should be (None)
    httpClient.pipeline should be (None)
    val config = Configuration(ServiceConfiguration(10, 10 seconds, 10 seconds), HostConfiguration())
    val updatedHttpClient = httpClient.update(config = Some(config))
    updatedHttpClient.name should be ("googlemap")
    updatedHttpClient.env should be (None)
    updatedHttpClient.config should be (Some(config))
    updatedHttpClient.pipeline should be (None)
  }

  "HttpClient.create('googlemap').get(uri) with correct endpoint (sleep 10s)" should "get restablish the connection and return StatusCodes.OK" in {
    Thread.sleep(10000)
    val response = HttpClientFactory.getOrCreate("googlemap").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient.create('googlemaphttps').get(uri) with correct endpoint" should "get StatusCodes.OK" in {
    val response = HttpClientFactory.getOrCreate("googlemaphttps").get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content.get.entity.nonEmpty should be (true)
    result.content.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient.create('googlemap').get(uri) with not existing uri" should "get StatusCodes.NotFound" in {
    val client = HttpClientFactory.getOrCreate("googlemap")
    val response = client.get("/api/elev")
    client.markUP
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
  }

  "Markdown HttpClient" should "throw out HttpClientException" in {
    val client = HttpClientFactory.getOrCreate("googlemap2")
    client.markDown
    val response = client.get("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val result = Await.result(response, 3 seconds)
    result.status should be (HttpClientException.httpClientMarkDownError)
    result.content.isLeft should be (true)
    result.content should be (Left(HttpClientMarkDownException("googlemap2")))
  }

  "HttpClient.create('googlemap').getEntity(uri) with correct endpoint" should "get correct Unmarshaller value" in {
    val response = HttpClientFactory.getOrCreate("googlemap").getEntity[GoogleApiResult[Elevation]]("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content should be (Right(GoogleApiResult[Elevation]("OK", List[Elevation](Elevation(Location(27.988056,86.925278), 8815.7158203125)))))
    result.rawHttpResponse.get.entity.nonEmpty should be (true)
    result.rawHttpResponse.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient.create('googlemap').getEntity(uri) with correct endpoint" should "get correct Unmarshaller value onComplete" in {
    val response = HttpClientFactory.getOrCreate("googlemap").getEntity[GoogleApiResult[Elevation]]("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    response onComplete {
      case Success(HttpResponseEntityWrapper(code, Right(r), rawResponse)) =>
        code should be (StatusCodes.OK)
        r.isInstanceOf[GoogleApiResult[Elevation]] should be (true)
        r.asInstanceOf[GoogleApiResult[Elevation]] should be (GoogleApiResult[Elevation]("OK", List[Elevation](Elevation(Location(27.988056,86.925278), 8815.7158203125))))
    }
  }

  "HttpClient.create('googlemaphttps').getEntity(uri) with correct endpoint" should "get correct Unmarshaller value" in {
    val response = HttpClientFactory.getOrCreate("googlemaphttps").getEntity[GoogleApiResult[Elevation]]("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.OK)
    result.content should be (Right(GoogleApiResult[Elevation]("OK", List[Elevation](Elevation(Location(27.988056,86.925278), 8815.7158203125)))))
    result.rawHttpResponse.get.entity.nonEmpty should be (true)
    result.rawHttpResponse.get.entity.data.nonEmpty should be (true)
  }

  "HttpClient.create('googlemap').getEntity(uri) with wrong uri" should "get StatusCodes.NotFound" in {
    val response = HttpClientFactory.getOrCreate("googlemap").getEntity[GoogleApiResult[Elevation]]("/api/elev")
    val result = Await.result(response, 3 seconds)
    result.status should be (StatusCodes.NotFound)
  }

  "HttpClient.create('googlemapnotexisting').getEntity(uri) with not existing endpoint" should "throw out ConnectionAttemptFailedException" in {
    val response = HttpClientFactory.getOrCreate("googlemapnotexisting").getEntity[GoogleApiResult[Elevation]]("/api/elevation/json?locations=27.988056,86.925278&sensor=false")
    response onComplete {
      case Failure(e) =>
        e.isInstanceOf[ConnectionAttemptFailedException] should be (true)
        e.getMessage should be ("Connection attempt to www.googlemapnotexisting.com:80 failed")
    }
  }
}

class GoogleMapEndpointResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      Some("http://maps.googleapis.com/maps")
    else
      None
  }

  override def name: String = "googlemap"
}

class GoogleMapHttpsEndpointResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      Some("https://maps.googleapis.com/maps")
    else
      None
  }

  override def name: String = "googlemaphttps"
}

class GoogleMap2EndpointResolverMarkdown extends EndpointResolver {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      Some("http://maps.googleapis.com/maps")
    else
      None
  }

  override def name: String = "googlemap2"
}

class GoogleMapNotExistingEndpointResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      Some("http://www.googlemapnotexisting.com")
    else
      None
  }

  override def name: String = "googlemapnotexisting"
}

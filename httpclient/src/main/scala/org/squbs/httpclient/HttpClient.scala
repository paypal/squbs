package org.squbs.httpclient

import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling.Marshaller
import scala.util.Try
import scala.concurrent._
import scala.annotation.tailrec
import org.squbs.httpclient.pipeline.{Pipeline, PipelineManager}
import scala.util.Failure
import scala.Some
import scala.util.Success
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.Duration
import spray.http.HttpRequest
import org.slf4j.LoggerFactory
import org.squbs.httpclient.env.{EnvironmentRegistry, Default, Environment}

/**
 * Created by hakuang on 5/9/14.
 */

object Status extends Enumeration {
  type Status = Value
  val UP, DOWN = Value
}

trait Client {

  var status = Status.UP

  val name: String

  val env: Environment

  val pipeline: Option[Pipeline]

  var endpoint = EndpointRegistry.resolve(name, env)

  def markUp = {
    status = Status.UP
  }

  def markDown = {
    status = Status.DOWN
  }
}

trait RetrySupport {

  val logger = LoggerFactory.getLogger(this.getClass)

  def retry[T](pipeline: HttpRequest => Future[T], httpRequest: HttpRequest, maxRetries: Int, requestTimeout: Duration): Future[T] = {

    @tailrec
    def doRetry(times: Int): Future[T] = {
      val work = pipeline {
        httpRequest
      }
      Try {
        Await.result(work, requestTimeout)
      } match {
        case Success(s) => work
        case Failure(throwable) if times == 0 => work
        case Failure(throwable) =>
          logger.info("Retry service [uri=" + httpRequest.uri.toString() + "] @ " + (maxRetries - times + 1) + " times")
          doRetry(times - 1)
      }
    }
    doRetry(maxRetries)
  }
}

trait ConfigurationSupport {
  def config(client: Client) = {
    client.endpoint match {
      case Some(endpoint) =>
        endpoint.config
      case None =>
        throw HttpClientEndpointNotExistException(client.name, client.env)
    }
  }

  def hostSettings(client: Client)(implicit actorSystem: ActorSystem) = {
    config(client).hostSettings
  }

  implicit def endpointToUri(endpoint: Option[Endpoint]): String = {
    endpoint.getOrElse(Endpoint("")).uri
  }
}

trait HttpCallSupport extends RetrySupport with ConfigurationSupport with PipelineManager {

  import ExecutionContext.Implicits.global

  def client: Client

  def handle(pipeline: Try[HttpRequest => Future[HttpResponseWrapper]], httpRequest: HttpRequest)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    val maxRetries = hostSettings(client).maxRetries
    val requestTimeout = hostSettings(client).connectionSettings.requestTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetries, requestTimeout)
      case Failure(t@HttpClientMarkDownException(_, _)) => future {
        HttpResponseWrapper(HttpClientException.httpClientMarkDownError, Left(t))
      }
      case Failure(t) => future {
        HttpResponseWrapper(999, Left(t))
      }
    }
  }

  def get(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Get(client.endpoint + uri))
  }

  def post[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Post(client.endpoint + uri, content))
  }

  def put[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Put(client.endpoint + uri, content))
  }

  def head(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Head(client.endpoint + uri))
  }

  def delete(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Delete(client.endpoint + uri))
  }

  def options(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(invokeToHttpResponse(client), Options(client.endpoint + uri))
  }
}

trait HttpEntityCallSupport extends RetrySupport with ConfigurationSupport with PipelineManager {

  import ExecutionContext.Implicits.global

  def client: Client

  def handleEntity[T: FromResponseUnmarshaller](pipeline: Try[HttpRequest => Future[HttpResponseEntityWrapper[T]]],
                                                httpRequest: HttpRequest)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[T]] = {
    val maxRetries = hostSettings(client).maxRetries
    val requestTimeout = hostSettings(client).connectionSettings.requestTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetries, requestTimeout)
      case Failure(t@HttpClientMarkDownException(_, _)) =>
        future {HttpResponseEntityWrapper[T](HttpClientException.httpClientMarkDownError, Left(t), None)}
      case Failure(t) =>
        future {HttpResponseEntityWrapper[T](999, Left(t), None)}
    }
  }

  def getEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Get(client.endpoint + uri))
  }

  def postEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Post(client.endpoint + uri, content))
  }

  def putEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Put(client.endpoint + uri, content))
  }

  def headEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Head(client.endpoint + uri))
  }

  def deleteEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Delete(client.endpoint + uri))
  }

  def optionsEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](invokeToEntity[R](client), Options(client.endpoint + uri))
  }
}

trait HttpClientSupport extends HttpCallSupport with HttpEntityCallSupport

case class HttpClient(name: String,
                      env: Environment = Default,
                      pipeline: Option[Pipeline] = None) extends Client with HttpClientSupport {

  require(endpoint != None, "endpoint should be resolved!")
  Endpoint.check(endpoint.get.uri)

  def client: Client = this

  def withConfig(config: Configuration): HttpClient = {
    endpoint = Some(Endpoint(endpoint.get.uri, config))
    HttpClientFactory.httpClientMap.put((name, env), this)
    this
  }
}

object HttpClientFactory {

  HttpClientJMX.registryBeans

  val httpClientMap: TrieMap[(String, Environment), HttpClient] = TrieMap[(String, Environment), HttpClient]()

  def getOrCreate(name: String): HttpClient = {
    getOrCreate(name, Default)
  }

  def getOrCreate(name: String, env: Environment): HttpClient = {
    getOrCreate(name, env, None)
  }

  def getOrCreate(name: String, env: Environment = Default, pipeline: Option[Pipeline] = None): HttpClient = {
    val newEnv = env match {
      case Default => EnvironmentRegistry.resolve(name)
      case _ => env
    }
    httpClientMap.getOrElseUpdate((name, newEnv), {
      HttpClient(name, newEnv, pipeline)
    })
  }
}
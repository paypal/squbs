package org.squbs.httpclient

import org.squbs.httpclient.endpoint.EndpointRegistry
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
import org.squbs.httpclient.config.{HostConfiguration, ServiceConfiguration, Configuration}
import scala.util.Success
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.Duration
import spray.http.HttpRequest

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

  val env: Option[String]

  val config: Option[Configuration]

  val pipeline: Option[Pipeline]

  val endpoint = EndpointRegistry.resolve(name, env).getOrElse("")

  def markUP = {
    status = Status.UP
  }

  def markDown = {
    status = Status.DOWN
  }
}

trait RetrySupport {
  def retry[T](pipeline: HttpRequest => Future[T], httpRequest: HttpRequest, maxRetryCount: Int, serviceTimeout: Duration): Future[T] = {

    @tailrec
    def doRetry(times: Int): Future[T] = {
      val work = pipeline {
        httpRequest
      }
      Try {
        Await.result(work, serviceTimeout)
      } match {
        case Success(s) => work
        case Failure(throwable) if times == 0 => work
        case Failure(throwable) =>
          println("Retry service [uri=" + httpRequest.uri.toString() + "] @ " + (maxRetryCount - times + 1) + " times")
          doRetry(times - 1)
      }
    }
    doRetry(maxRetryCount)
  }
}

trait HttpCallSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def client: Client

  def handle(pipeline: Try[HttpRequest => Future[HttpResponseWrapper]], httpRequest: HttpRequest): Future[HttpResponseWrapper] = {
    val config = client.config
    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t@HttpClientMarkDownException(_, _)) => future {
        HttpResponseWrapper(HttpClientException.httpClientMarkDownError, Left(t))
      }
      case Failure(t) => future {
        HttpResponseWrapper(999, Left(t))
      }
    }
  }

  def get(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(client), Get(client.endpoint + uri))
  }

  def post[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(client), Post(client.endpoint + uri, content))
  }

  def put[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(client), Put(client.endpoint + uri, content))
  }

  def head(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(client), Head(client.endpoint + uri))
  }

  def delete(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(client), Delete(client.endpoint + uri))
  }

  def options(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(client), Options(client.endpoint + uri))
  }
}

trait HttpEntityCallSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def client: Client

  def handleEntity[T: FromResponseUnmarshaller](pipeline: Try[HttpRequest => Future[HttpResponseEntityWrapper[T]]],
                                                httpRequest: HttpRequest): Future[HttpResponseEntityWrapper[T]] = {
    val config = client.config
    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t@HttpClientMarkDownException(_, _)) => future {
        HttpResponseEntityWrapper(HttpClientException.httpClientMarkDownError, Left(t), None)
      }
      case Failure(t) => future {
        HttpResponseEntityWrapper(999, Left(t), None)
      }
    }
  }

  def getEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](client), Get(client.endpoint + uri))
  }

  def postEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](client), Post(client.endpoint + uri, content))
  }

  def putEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](client), Put(client.endpoint + uri, content))
  }

  def headEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](client), Head(client.endpoint + uri))
  }

  def deleteEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](client), Delete(client.endpoint + uri))
  }

  def optionsEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](client), Options(client.endpoint + uri))
  }
}

trait HttpClientSupport extends HttpCallSupport with HttpEntityCallSupport

case class HttpClient(name: String,
                      env: Option[String] = None,
                      config: Option[Configuration] = None,
                      pipeline: Option[Pipeline] = None) extends Client with HttpClientSupport {

  require(endpoint.toLowerCase.startsWith("http://") || endpoint.toLowerCase.startsWith("https://"), "endpoint should be start with http:// or https://")

  def client: Client = this

  def update(config: Option[Configuration] = None, pipeline: Option[Pipeline] = None): Client = {
    val httpClient = HttpClient(name, env, config, pipeline)
    HttpClientFactory.httpClientMap.put((name,env),httpClient)
    httpClient
  }
}

object HttpClientFactory {

  val httpClientMap: TrieMap[(String, Option[String]), HttpClient] = TrieMap[(String, Option[String]), HttpClient]()

  def getOrCreate(name: String): HttpClient = {
    getOrCreate(name, env = None)
  }

  def getOrCreate(name: String, env: String): HttpClient = {
    getOrCreate(name, env = Some(env))
  }

  def getOrCreate(name: String, env: Option[String] = None, config: Option[Configuration] = None, pipeline: Option[Pipeline] = None): HttpClient = {
    httpClientMap.getOrElseUpdate((name, env), {
      HttpClient(name, env, config, pipeline)
    })
  }
}
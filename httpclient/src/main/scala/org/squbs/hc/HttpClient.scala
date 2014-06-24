package org.squbs.hc

import org.squbs.hc.routing.RoutingRegistry
import akka.actor.{ActorRef, ActorSystem}
import spray.client.pipelining._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling.Marshaller
import scala.util.Try
import scala.concurrent._
import scala.annotation.tailrec
import org.squbs.hc.pipeline.{PipelineDefinition, PipelineManager}
import spray.http.HttpRequest
import scala.util.Failure
import scala.Some
import org.squbs.hc.config.{HostConfiguration, ServiceConfiguration, Configuration}
import scala.util.Success
import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.Duration
import org.squbs.unicomplex.JMX
import org.squbs.unicomplex.JMX._
import org.squbs.hc.jmx.HttpClientBean
import spray.http.HttpRequest
import spray.client.pipelining

/**
 * Created by hakuang on 5/9/14.
 */
trait IHttpClient {

  var status = HttpClientStatus.UP

  def name: String

  def endpoint: String

  def config: Option[Configuration]

  def pipelineDefinition: Option[PipelineDefinition]
}

/**
 * Without setup HttpConnection
 */
trait HttpCallActorSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def handle(httpClient: IHttpClient,
             pipeline: Try[HttpRequest => Future[HttpResponseWrapper]],
             httpRequest: HttpRequest): Future[HttpResponseWrapper] = {
    val config = httpClient.config
    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t@ServiceMarkDownException(_, _, _)) => future {
        HttpResponseWrapper(HttpClientException.serviceMarkDownError, Left(t))
      }
      case Failure(t) => future {
        HttpResponseWrapper(999, Left(t))
      }
    }
  }

  def get(httpClient: IHttpClient, actorRef: ActorRef, uri: String)
         (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(httpClient, PipelineManager.invokeToHttpResponseWithoutSetup(httpClient, actorRef), Get(httpClient.endpoint + uri))
  }

  def post[T: Marshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String, content: Some[T])
                         (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(httpClient, PipelineManager.invokeToHttpResponseWithoutSetup(httpClient, actorRef), Post(httpClient.endpoint + uri, content))
  }

  def put[T: Marshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String, content: Some[T])
                        (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(httpClient, PipelineManager.invokeToHttpResponseWithoutSetup(httpClient, actorRef), Put(httpClient.endpoint + uri, content))
  }

  def head(httpClient: IHttpClient, actorRef: ActorRef, uri: String)
          (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(httpClient, PipelineManager.invokeToHttpResponseWithoutSetup(httpClient, actorRef), Head(httpClient.endpoint + uri))
  }

  def delete(httpClient: IHttpClient, actorRef: ActorRef, uri: String)
            (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(httpClient, PipelineManager.invokeToHttpResponseWithoutSetup(httpClient, actorRef), Delete(httpClient.endpoint + uri))
  }

  def options(httpClient: IHttpClient, actorRef: ActorRef, uri: String)
             (implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(httpClient, PipelineManager.invokeToHttpResponseWithoutSetup(httpClient, actorRef), Options(httpClient.endpoint + uri))
  }
}

trait HttpCallSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def httpClient: IHttpClient

  def handle(pipeline: Try[HttpRequest => Future[HttpResponseWrapper]], httpRequest: HttpRequest): Future[HttpResponseWrapper] = {
    val config = httpClient.config
    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t@ServiceMarkDownException(_, _, _)) => future {
        HttpResponseWrapper(HttpClientException.serviceMarkDownError, Left(t))
      }
      case Failure(t) => future {
        HttpResponseWrapper(999, Left(t))
      }
    }
  }

  def get(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClient), Get(httpClient.endpoint + uri))
  }

  def post[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClient), Post(httpClient.endpoint + uri, content))
  }

  def put[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClient), Put(httpClient.endpoint + uri, content))
  }

  def head(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClient), Head(httpClient.endpoint + uri))
  }

  def delete(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClient), Delete(httpClient.endpoint + uri))
  }

  def options(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClient), Options(httpClient.endpoint + uri))
  }
}

/**
 * Without setup HttpConnection
 */
trait HttpEntityCallActorSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def handleEntity[T: FromResponseUnmarshaller](httpClient: IHttpClient,
                                                pipeline: Try[HttpRequest => Future[HttpResponseEntityWrapper[T]]],
                                                httpRequest: HttpRequest): Future[HttpResponseEntityWrapper[T]] = {
    val config = httpClient.config
    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t@ServiceMarkDownException(_, _, _)) => future {
        HttpResponseEntityWrapper(HttpClientException.serviceMarkDownError, Left(t), None)
      }
      case Failure(t) => future {
        HttpResponseEntityWrapper(999, Left(t), None)
      }
    }
  }

  def getEntity[R: FromResponseUnmarshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String)
                                            (implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](httpClient, PipelineManager.invokeToEntityWithoutSetup[R](httpClient, actorRef), Get(httpClient.endpoint + uri))
  }

  def postEntity[T: Marshaller, R: FromResponseUnmarshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String, content: Some[T])
                                                            (implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](httpClient, PipelineManager.invokeToEntityWithoutSetup[R](httpClient, actorRef), Post(httpClient.endpoint + uri, content))
  }

  def putEntity[T: Marshaller, R: FromResponseUnmarshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String, content: Some[T])
                                                           (implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](httpClient, PipelineManager.invokeToEntityWithoutSetup[R](httpClient, actorRef), Put(httpClient.endpoint + uri, content))
  }

  def headEntity[R: FromResponseUnmarshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String)
                                             (implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](httpClient, PipelineManager.invokeToEntityWithoutSetup[R](httpClient, actorRef), Head(httpClient.endpoint + uri))
  }

  def deleteEntity[R: FromResponseUnmarshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String)
                                               (implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](httpClient, PipelineManager.invokeToEntityWithoutSetup[R](httpClient, actorRef), Delete(httpClient.endpoint + uri))
  }

  def optionsEntity[R: FromResponseUnmarshaller](httpClient: IHttpClient, actorRef: ActorRef, uri: String)
                                                (implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](httpClient, PipelineManager.invokeToEntityWithoutSetup[R](httpClient, actorRef), Options(httpClient.endpoint + uri))
  }
}

trait HttpEntityCallSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def httpClient: IHttpClient

  def handleEntity[T: FromResponseUnmarshaller](pipeline: Try[HttpRequest => Future[HttpResponseEntityWrapper[T]]],
                                                httpRequest: HttpRequest): Future[HttpResponseEntityWrapper[T]] = {
    val config = httpClient.config
    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t@ServiceMarkDownException(_, _, _)) => future {
        HttpResponseEntityWrapper(HttpClientException.serviceMarkDownError, Left(t), None)
      }
      case Failure(t) => future {
        HttpResponseEntityWrapper(999, Left(t), None)
      }
    }
  }

  def getEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClient), Get(httpClient.endpoint + uri))
  }

  def postEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClient), Post(httpClient.endpoint + uri, content))
  }

  def putEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClient), Put(httpClient.endpoint + uri, content))
  }

  def headEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClient), Head(httpClient.endpoint + uri))
  }

  def deleteEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClient), Delete(httpClient.endpoint + uri))
  }

  def optionsEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClient), Options(httpClient.endpoint + uri))
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

trait HttpClientSupport extends HttpCallSupport with HttpEntityCallSupport

case class HttpClient(name: String,
                      endpoint: String,
                      config: Option[Configuration] = None,
                      pipelineDefinition: Option[PipelineDefinition] = None) extends IHttpClient with HttpClientSupport {

  require(endpoint.toLowerCase.startsWith("http://") || endpoint.toLowerCase.startsWith("https://"), "endpoint should be start with http:// or https://")

  def markUP = {
    status = HttpClientStatus.UP
  }

  def markDown = {
    status = HttpClientStatus.DOWN
  }

  def httpClient: IHttpClient = this
}

object HttpClientStatus extends Enumeration {
  type HttpClientStatus = Value
  val UP, DOWN = Value
}

object HttpClient {

  private val httpClientMap: TrieMap[String, HttpClient] = TrieMap[String, HttpClient]()

  if (!JMX.isRegistered(HttpClientBean.HTTPCLIENTNFO)) JMX.register(HttpClientBean, HttpClientBean.HTTPCLIENTNFO)

  def create(svcName: String): HttpClient = {
    create(svcName, env = None)
  }

  def create(svcName: String, env: String): HttpClient = {
    create(svcName, env = Some(env))
  }

  def create(svcName: String, config: Configuration): HttpClient = {
    create(svcName, config = Some(config))
  }

  def create(svcName: String, env: Option[String] = None, config: Option[Configuration] = None, pipeline: Option[PipelineDefinition] = None): HttpClient = {
    httpClientMap.getOrElseUpdate(svcName, {
      val endpoint = RoutingRegistry.resolve(svcName, env).getOrElse("")
      HttpClient(svcName, endpoint, config, pipeline)
    })
  }

  def clear = {
    httpClientMap.clear()
  }

  def get = httpClientMap

  def remove(svcName: String) = {
    httpClientMap.remove(svcName)
  }
}
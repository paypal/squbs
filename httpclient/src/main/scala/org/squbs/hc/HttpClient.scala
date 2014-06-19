package org.squbs.hc

import org.squbs.hc.routing.RoutingRegistry
import akka.actor.ActorSystem
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

/**
 * Created by hakuang on 5/9/14.
 */
trait HttpCallSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def httpClientInstance: HttpClient

  def handle(pipeline: Try[HttpRequest => Future[HttpResponseWrapper]], httpRequest: HttpRequest): Future[HttpResponseWrapper] = {
    val maxRetryCount = httpClientInstance.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = httpClientInstance.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t @ ServiceMarkDownException(_, _, _)) => future{HttpResponseWrapper(HttpClientException.serviceMarkDownError, Left(t))}
      case Failure(t) => future{HttpResponseWrapper(999, Left(t))}
    }
  }

  def get(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClientInstance), Get(httpClientInstance.endpoint + uri))
  }

  def post[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClientInstance), Post(httpClientInstance.endpoint + uri, content))
  }

  def put[T: Marshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClientInstance), Put(httpClientInstance.endpoint + uri, content))
  }

  def head(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClientInstance), Head(httpClientInstance.endpoint + uri))
  }

  def delete(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClientInstance), Delete(httpClientInstance.endpoint + uri))
  }

  def options(uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(httpClientInstance), Options(httpClientInstance.endpoint + uri))
  }

}

trait HttpEntityCallSupport extends RetrySupport {

  import ExecutionContext.Implicits.global

  def httpClientInstance: HttpClient

  def handleEntity[T: FromResponseUnmarshaller](pipeline: Try[HttpRequest => Future[HttpResponseEntityWrapper[T]]], httpRequest: HttpRequest): Future[HttpResponseEntityWrapper[T]] = {
    val maxRetryCount = httpClientInstance.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount
    val serviceTimeout = httpClientInstance.config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout
    pipeline match {
      case Success(res) => retry(res, httpRequest, maxRetryCount, serviceTimeout)
      case Failure(t @ ServiceMarkDownException(_, _, _)) => future{HttpResponseEntityWrapper(HttpClientException.serviceMarkDownError, Left(t), None)}
      case Failure(t) => future{HttpResponseEntityWrapper(999, Left(t), None)}
    }
  }

  def getEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClientInstance), Get(httpClientInstance.endpoint + uri))
  }

  def postEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClientInstance), Post(httpClientInstance.endpoint + uri, content))
  }

  def putEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T])(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClientInstance), Put(httpClientInstance.endpoint + uri, content))
  }

  def headEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClientInstance), Head(httpClientInstance.endpoint + uri))
  }

  def deleteEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClientInstance), Delete(httpClientInstance.endpoint + uri))
  }

  def optionsEntity[R: FromResponseUnmarshaller](uri: String)(implicit actorSystem: ActorSystem): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](httpClientInstance), Options(httpClientInstance.endpoint + uri))
  }
}

trait RetrySupport {
  def retry[T](pipeline: HttpRequest => Future[T], httpRequest: HttpRequest, maxRetryCount: Int, serviceTimeout: Duration): Future[T] = {

    @tailrec
    def doRetry(times: Int): Future[T] = {
      val work = pipeline{httpRequest}
      Try{Await.result(work, serviceTimeout)} match {
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

case class HttpClient(name: String, endpoint: String, config: Option[Configuration] = None, pipelineDefinition: Option[PipelineDefinition] = None) extends HttpClientSupport{

  require(endpoint.toLowerCase.startsWith("http://") || endpoint.toLowerCase.startsWith("https://"), "endpoint should be start with http:// or https://")

  def httpClientInstance = this

  var status = HttpClientStatus.UP

  def markUP = {
    status = HttpClientStatus.UP
  }

  def markDown = {
    status = HttpClientStatus.DOWN
  }

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
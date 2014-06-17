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
import org.squbs.hc.jmx.{HttpClientBean, JMX}
import org.squbs.hc.jmx.JMX._

/**
 * Created by hakuang on 5/9/14.
 */

case class HttpClient(name: String, endpoint: String, config: Option[Configuration] = None, pipelineDefinition: Option[PipelineDefinition] = None)(implicit system: ActorSystem) {

  import ExecutionContext.Implicits.global

  require(endpoint.toLowerCase.startsWith("http://") || endpoint.toLowerCase.startsWith("https://"), "endpoint should be start with http:// or https://")

  var status = HttpClientStatus.UP

  def markUP = {
    status = HttpClientStatus.UP
  }

  def markDown = {
    status = HttpClientStatus.DOWN
  }

  protected def handle(pipeline: Try[HttpRequest => Future[HttpResponseWrapper]], httpRequest: HttpRequest): Future[HttpResponseWrapper] = {
    pipeline match {
      case Success(res) => retry(res, httpRequest)
      case Failure(t @ ServiceMarkDownException(_, _, _)) => future{HttpResponseWrapper(HttpClientException.serviceMarkDownError, Left(t))}
      case Failure(t) => future{HttpResponseWrapper(999, Left(t))}
    }
  }

  protected def handleEntity[T: FromResponseUnmarshaller](pipeline: Try[HttpRequest => Future[HttpResponseEntityWrapper[T]]], httpRequest: HttpRequest): Future[HttpResponseEntityWrapper[T]] = {
    pipeline match {
      case Success(res) => retry(res, httpRequest)
      case Failure(t @ ServiceMarkDownException(_, _, _)) => future{HttpResponseEntityWrapper(HttpClientException.serviceMarkDownError, Left(t), None)}
      case Failure(t) => future{HttpResponseEntityWrapper(999, Left(t), None)}
    }
  }

  protected def retry[T](pipeline: HttpRequest => Future[T], httpRequest: HttpRequest): Future[T] = {

    val maxRetryCount = config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.maxRetryCount

    @tailrec
    def doRetry(times: Int): Future[T] = {
      val work = pipeline{httpRequest}
      Try{Await.result(work, config.getOrElse(Configuration(ServiceConfiguration(), HostConfiguration())).svcConfig.serviceTimeout)} match {
        case Success(s) => work
        case Failure(throwable) if times == 0 => work
        case Failure(throwable) =>
          println("Retry service [uri=" + httpRequest.uri.toString() + "] @ " + (maxRetryCount - times + 1) + " times")
          doRetry(times - 1)
      }
    }
    doRetry(maxRetryCount)
  }

  def get(uri: String): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(this), Get(endpoint + uri))
  }

  def post[T: Marshaller](uri: String, content: Some[T]): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(this), Post(endpoint + uri, content))
  }

  def put[T: Marshaller](uri: String, content: Some[T]): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(this), Put(endpoint + uri, content))
  }

  def head(uri: String): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(this), Head(endpoint + uri))
  }

  def delete(uri: String): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(this), Delete(endpoint + uri))
  }

  def options(uri: String): Future[HttpResponseWrapper] = {
    handle(PipelineManager.invokeToHttpResponse(this), Options(endpoint + uri))
  }

  def getEntity[R: FromResponseUnmarshaller](uri: String): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](this), Get(endpoint + uri))
  }

  def postEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T]): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](this), Post(endpoint + uri, content))
  }

  def putEntity[T: Marshaller, R: FromResponseUnmarshaller](uri: String, content: Some[T]): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](this), Put(endpoint + uri, content))
  }

  def headEntity[R: FromResponseUnmarshaller](uri: String): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](this), Head(endpoint + uri))
  }

  def deleteEntity[R: FromResponseUnmarshaller](uri: String): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](this), Delete(endpoint + uri))
  }

  def optionsEntity[R: FromResponseUnmarshaller](uri: String): Future[HttpResponseEntityWrapper[R]] = {
    handleEntity[R](PipelineManager.invokeToEntity[R](this), Options(endpoint + uri))
  }
}

object HttpClientStatus extends Enumeration {
  type HttpClientStatus = Value
  val UP, DOWN = Value
}

object HttpClient {

  private val httpClientMap: TrieMap[String, HttpClient] = TrieMap[String, HttpClient]()

  if (!JMX.isRegistered(JMX.HTTPCLIENTNFO)) JMX.register(HttpClientBean, JMX.HTTPCLIENTNFO)

  def create(svcName: String)(implicit system:ActorSystem): HttpClient = {
    create(svcName, env = None)
  }

  def create(svcName: String, env: String)(implicit system:ActorSystem): HttpClient = {
    create(svcName, env = Some(env))
  }

  def create(svcName: String, config: Configuration)(implicit system:ActorSystem): HttpClient = {
    create(svcName, config = Some(config))
  }

  def create(svcName: String, env: Option[String] = None, config: Option[Configuration] = None, pipeline: Option[PipelineDefinition] = None)(implicit system:ActorSystem): HttpClient = {
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
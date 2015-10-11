/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.squbs.httpclient

import akka.actor._
import akka.pattern.{CircuitBreaker, _}
import org.squbs.httpclient.Configuration._
import org.squbs.httpclient.HttpClientActorMessage.{MarkDownSuccess, MarkUpSuccess}
import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.httpclient.env.{Default, Environment, EnvironmentRegistry}
import org.squbs.httpclient.pipeline.HttpClientUnmarshal._
import org.squbs.pipeline.{PipelineSetting, SimplePipelineConfig}
import spray.http.{HttpResponse, Uri}
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._

import scala.concurrent.Future
import scala.language.postfixOps

object Status extends Enumeration {
  type Status = Value
  val UP, DOWN = Value
}

trait HttpClientPathBuilder {

  def buildRequestUri(path: String, paramMap: Map[String, Any] = Map.empty[String, Any]): String = {
    if (paramMap.isEmpty) {
      Uri(path).toString()
    } else {
      val trimPath =
        if (path.length > 1 && path.charAt(path.length - 1) == '/') path.substring(0, path.length - 1)
        else path
      val filteredParamMap = paramMap collect {
        case (k, v: String) => (k, v)
        case (k, v: Double) => (k, v.toString)
        case (k, v: Float) => (k, v.toString)
        case (k, v: Long) => (k, v.toString)
        case (k, v: Int) => (k, v.toString)
        case (k, v: Short) => (k, v.toString)
        case (k, v: Byte) => (k, v.toString)
        case (k, v: Char) => (k, v.toString)
        case (k, v: Boolean) => (k, v.toString)
      }
      Uri(trimPath).withQuery(filteredParamMap).toString()
    }
  }
}

object HttpClientPathBuilder extends HttpClientPathBuilder

case class HttpClient(name: String,
                      env: Environment = Default,
                      status: Status.Status = Status.UP,
                      config: Option[Configuration] = None)
                     (actorCreator: (ActorRefFactory) => Future[ActorRef] = {(arf) =>
                        Future.successful(arf.actorOf(Props(classOf[HttpClientActor], name, env)))
                      })(implicit actorRefFactory: ActorRefFactory) { self =>

  implicit val ec = actorRefFactory.dispatcher

  val system: ActorSystem = actorRefFactory
  val fActorRef = actorCreator(actorRefFactory)

  val endpoint = {
    val serviceEndpoint = EndpointRegistry(system).resolve(name, env)
    serviceEndpoint match {
      case Some(se) => config match {
        case Some(conf) => se.copy(config = conf)
        case None => se
      }
      case None => throw HttpClientEndpointNotExistException(name, env)
    }
  }

  import endpoint.config.settings.{circuitBreakerConfig => cbConfig}

  private[httpclient] var cbStat = CircuitBreakerStatus.Closed
  private[httpclient] val cbMetrics =
    new CircuitBreakerMetrics(cbConfig.historyUnits, cbConfig.historyUnitDuration)(system)
  private[httpclient] val cb: CircuitBreaker = new CircuitBreaker(system.scheduler, cbConfig.maxFailures,
    cbConfig.callTimeout, cbConfig.resetTimeout)(system.dispatcher)


  cb.onClose{
    cbStat = CircuitBreakerStatus.Closed
  }

  cb.onOpen{
    cbStat = CircuitBreakerStatus.Open
  }

  cb.onHalfOpen{
    cbStat = CircuitBreakerStatus.HalfOpen
  }

  def get[R](uri: String, reqSettings: RequestSettings)
            (implicit unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    val fHttpResponse = fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Get(uri, Option(reqSettings))).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def get[R](uri: String)(implicit unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = defaultTimeout
    val fHttpResponse = fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Get(uri, None)).mapTo[HttpResponse] }
    unmarshall[R](fHttpResponse)
  }

  def options[R](uri: String, reqSettings: RequestSettings)
                (implicit unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    val fHttpResponse = fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Options(uri, Option(reqSettings))).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def options[R](uri: String)(implicit unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = defaultTimeout
    val fHttpResponse = fActorRef flatMap {
      ref => (ref ? HttpClientActorMessage.Options(uri, None)).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def delete[R](uri: String, reqSettings: RequestSettings)
               (implicit unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    val fHttpResponse = fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Delete(uri, Option(reqSettings))).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def delete[R](uri: String)(implicit unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = defaultTimeout
    val fHttpResponse = fActorRef flatMap {
      ref => (ref ? HttpClientActorMessage.Delete(uri, None)).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def post[T, R](uri: String, content: Option[T], reqSettings: RequestSettings)
                (implicit marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    val fHttpResponse = fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Post(uri, content, marshaller, Option(reqSettings))).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def post[T, R](uri: String, content: Option[T])
                (implicit marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = defaultTimeout
    val fHttpResponse = fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Post(uri, content, marshaller, None)).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def put[T, R](uri: String, content: Option[T], reqSettings: RequestSettings)
               (implicit marshaller: Marshaller[T], unmarshaller : FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    val fHttpResponse = fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Put(uri, content, marshaller, Option(reqSettings))).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  def put[T, R](uri: String, content: Option[T])
               (implicit marshaller: Marshaller[T], unmarshaller : FromResponseUnmarshaller[R]): Future[R] = {
    implicit val timeout = defaultTimeout
    val fHttpResponse = fActorRef flatMap {
      ref => (ref ? HttpClientActorMessage.Put(uri, content, marshaller, None)).mapTo[HttpResponse]
    }
    unmarshall[R](fHttpResponse)
  }

  private def unmarshall[R](httpResponse: Future[HttpResponse])
                           (implicit ummarshaller : FromResponseUnmarshaller[R]) : Future[R] = {
    httpResponse flatMap { response =>
      try {
        Future.fromTry(response.unmarshalTo[R])
      } catch {
        case t: Throwable => Future.failed(t)
      }
    }
  }

  def raw = new RawHttpClient(this)

  val defaultTimeout = Configuration.defaultRequestSettings(endpoint.config, config).timeout.askTimeout

  def withConfig(config: Configuration): HttpClient = {
    implicit val timeout = defaultFutureTimeout
    HttpClient(name, env, status, Some(config))(
      (_) => fActorRef flatMap { ref => (ref ? HttpClientActorMessage.UpdateConfig(config)).mapTo[ActorRef] }
    )
  }

  def withSettings(settings: Settings): HttpClient = {
    implicit val timeout = defaultFutureTimeout
    HttpClient(name, env, status, Some(config.getOrElse(Configuration()).copy(settings = settings)))(
      (_) => fActorRef flatMap { ref => (ref ? HttpClientActorMessage.UpdateSettings(settings)).mapTo[ActorRef] }
    )
  }

  @deprecated
  def withPipeline(pipeline: Option[SimplePipelineConfig]): HttpClient = {
    withPipelineSetting(Some(PipelineSetting(config = pipeline)))
  }

  def withPipelineSetting(pipelineSetting: Option[PipelineSetting]): HttpClient = {
    implicit val timeout = defaultFutureTimeout
    HttpClient(name, env, status, Some(config.getOrElse(Configuration()).copy(pipeline = pipelineSetting)))(
      (_) => fActorRef flatMap { ref => (ref ? HttpClientActorMessage.UpdatePipeline(pipelineSetting)).mapTo[ActorRef] }
    )
  }

  def markDown: Future[MarkDownSuccess.type] = {
    implicit val timeout = defaultFutureTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.MarkDown).mapTo[MarkDownSuccess.type] }
  }

  def markUp: Future[MarkUpSuccess.type] = {
    implicit val timeout = defaultFutureTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.MarkUp).mapTo[MarkUpSuccess.type] }
  }

  def readyFuture: Future[Unit] = {
    fActorRef map {_ => }
  }
}

class RawHttpClient(private val client : HttpClient) {
  import client._

  def get(uri: String, reqSettings: RequestSettings): Future[HttpResponse] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Get(uri, Option(reqSettings))).mapTo[HttpResponse] }
  }

  def get(uri: String): Future[HttpResponse] = {
    implicit val timeout = defaultTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Get(uri, None)).mapTo[HttpResponse] }
  }

  def post[T](uri: String, content: Option[T], reqSettings: RequestSettings)
             (implicit marshaller: Marshaller[T]): Future[HttpResponse] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Post[T](uri, content, marshaller, Option(reqSettings))).mapTo[HttpResponse]
    }
  }

  def post[T](uri: String, content: Option[T])(implicit marshaller: Marshaller[T]): Future[HttpResponse] = {
    implicit val timeout = defaultTimeout
    fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Post[T](uri, content, marshaller, None)).mapTo[HttpResponse]
    }
  }

  def put[T](uri: String, content: Option[T], reqSettings: RequestSettings)
            (implicit marshaller: Marshaller[T]): Future[HttpResponse] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Put[T](uri, content, marshaller, Option(reqSettings))).mapTo[HttpResponse]
    }
  }

  def put[T](uri: String, content: Option[T])(implicit marshaller: Marshaller[T]): Future[HttpResponse] = {
    implicit val timeout = defaultTimeout
    fActorRef flatMap { ref =>
      (ref ? HttpClientActorMessage.Put[T](uri, content, marshaller, None)).mapTo[HttpResponse]
    }
  }

  def head(uri: String, reqSettings: RequestSettings): Future[HttpResponse] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Head(uri, Option(reqSettings))).mapTo[HttpResponse] }
  }

  def head(uri: String): Future[HttpResponse] = {
    implicit val timeout = defaultTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Head(uri, None)).mapTo[HttpResponse]}
  }

  def delete(uri: String, reqSettings: RequestSettings): Future[HttpResponse] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Delete(uri, Option(reqSettings))).mapTo[HttpResponse] }
  }

  def delete(uri: String): Future[HttpResponse] = {
    implicit val timeout = defaultTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Delete(uri, None)).mapTo[HttpResponse]}
  }

  def options(uri: String, reqSettings: RequestSettings): Future[HttpResponse] = {
    implicit val timeout = reqSettings.timeout.askTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Options(uri, Option(reqSettings))).mapTo[HttpResponse] }
  }

  def options(uri: String): Future[HttpResponse] = {
    implicit val timeout = defaultTimeout
    fActorRef flatMap { ref => (ref ? HttpClientActorMessage.Options(uri, None)).mapTo[HttpResponse] }
  }
}

object HttpClientFactory {

  def get(name: String, env: Environment = Default)(implicit system: ActorSystem): HttpClient = {
    val newEnv = env match {
      case Default => EnvironmentRegistry(system).resolve(name)
      case _ => env
    }
    implicit val timeout = defaultFutureTimeout
    HttpClient(name, newEnv, Status.UP, None)(
    (_) => (HttpClientManager(system).httpClientManager ? HttpClientManagerMessage.Get(name, newEnv)).mapTo[ActorRef]
    )
  }
}
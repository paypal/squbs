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

package org.squbs.httpclient.pipeline

import javax.net.ssl.SSLContext

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.squbs.httpclient._
import org.squbs.httpclient.endpoint.Endpoint
import org.squbs.httpclient.pipeline.impl.RequestUpdateHeadersHandler
import org.squbs.pipeline.PipelineProcessorActor._
import org.squbs.pipeline.{RequestContext, PipelineProcessorActor, Processor, PipelineSetting}
import spray.can.Http
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}
import spray.httpx.unmarshalling._
import spray.httpx.{PipelineException, UnsuccessfulResponseException}
import spray.io.ClientSSLEngineProvider

import scala.concurrent.Future
import scala.util.Try

object HttpClientUnmarshal{

  implicit class HttpResponseUnmarshal(val response: HttpResponse) extends AnyVal {

    def unmarshalTo[T: FromResponseUnmarshaller]: Try[T] = {
      Try{
        if (response.status.isSuccess)
          response.as[T] match {
            case Right(value) ⇒ value
            case Left(error) ⇒ throw new PipelineException(error.toString)
          }
        else throw new UnsuccessfulResponseException(response)
      }
    }
  }
}

trait PipelineManager extends LazyLogging {

  val httpClientLogger = logger

  def hostConnectorSetup(client: HttpClient, reqSettings: RequestSettings)(implicit system: ActorSystem) = {
    implicit def sslContext: SSLContext = {
      client.endpoint.config.settings.sslContext match {
        case Some(context) => context
        case None          => SSLContext.getDefault
      }
    }

    implicit val myClientEngineProvider = ClientSSLEngineProvider { engine =>
      engine
    }

    import client.endpoint.config.settings.hostSettings.connectionSettings
    val clientConnectionSettings = reqSettings match {
      case Configuration.defaultRequestSettings =>
        val reqTimeout = Configuration.defaultRequestSettings(client.endpoint.config, client.config).timeout
        connectionSettings.copy(requestTimeout = reqTimeout.duration)
      case _                                    =>
        connectionSettings.copy(requestTimeout = reqSettings.timeout.duration)
    }
    val hostSettings = client.endpoint.config.settings.hostSettings.copy(connectionSettings = clientConnectionSettings)
    import client.endpoint._
    Http.HostConnectorSetup(host, port, isSecure).copy(
      settings = Some(hostSettings), connectionType = client.endpoint.config.settings.connectionType)
  }

  def invokeToHttpResponseWithoutSetup(client: HttpClient, reqSettings: RequestSettings, actorRef: ActorRef)
                                      (implicit system: ActorSystem): Try[(HttpRequest => Future[HttpResponse])] = {
    implicit val ec = system.dispatcher
    val pipelineSetting = client.endpoint.config.pipeline.getOrElse(PipelineSetting.default)
    val pipeConfig = pipelineSetting.pipelineConfig
    implicit val timeout: Timeout =
      client.endpoint.config.settings.hostSettings.connectionSettings.connectingTimeout.toMillis
    val pipeline = spray.client.pipelining.sendReceive(actorRef)
    val updatedPipeConfig = reqSettings.headers.isEmpty match {
      case false =>
        val requestPipelines = pipeConfig.reqPipe :+ new RequestUpdateHeadersHandler(reqSettings.headers)
        pipeConfig.copy(reqPipe = requestPipelines)
      case true => pipeConfig
    }

    val processor = pipelineSetting.factory.create(updatedPipeConfig, pipelineSetting.setting)


    Try {
      client.status match {
        case Status.DOWN =>
          throw HttpClientMarkDownException(client.name, client.env)
        case _ =>
          req: HttpRequest =>
            processor match {
              case None => pipeline(req)
              case Some(proc) =>
                val pipelineTarget : PipelineTarget = (req, caller) => pipeline(req.asInstanceOf[HttpRequest]) pipeTo caller
                val pipeProxy = system.actorOf(Props(classOf[PipelineProcessorActor], pipelineTarget, proc))
                (pipeProxy ? (RequestContext(req) +> ("HttpClient.name" -> client.name, "HttpClient.Endpoint" -> client.endpoint))).mapTo[HttpResponse]
            }
      }
    }
  }

  implicit def endpointToUri(endpoint: Endpoint): String = {
    val uri = endpoint.uri
    val host = uri.authority.host.toString
    val port = if (uri.effectivePort == 0) 80 else uri.effectivePort
    val isSecure = uri.scheme.toLowerCase.equals("https")
    (isSecure, port) match {
      case (true, 443) => s"https://$host"
      case (true, _)   => s"https://$host:$port"
      case (false, 80) => s"http://$host"
      case (false, _)  => s"http://$host:$port"
    }
  }
}
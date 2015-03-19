/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient.pipeline

import org.squbs.httpclient.pipeline.impl.RequestUpdateHeaderHandler
import spray.httpx.{UnsuccessfulResponseException, PipelineException}
import akka.actor.{Props, ActorRef, ActorSystem}
import spray.httpx.unmarshalling._
import scala.concurrent.Future
import org.squbs.httpclient._
import spray.http.{Uri, HttpRequest, HttpResponse}
import scala.util.Try
import akka.pattern._
import akka.util.Timeout
import spray.can.Http
import javax.net.ssl.SSLContext
import spray.io.ClientSSLEngineProvider
import org.squbs.httpclient.endpoint.Endpoint
import org.slf4j.LoggerFactory
import org.squbs.proxy.SimplePipelineConfig

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

trait PipelineManager{

  val httpClientLogger = LoggerFactory.getLogger(this.getClass)


  def hostConnectorSetup(client: HttpClient,
                         reqSettings: RequestSettings = Configuration.defaultRequestSettings)(implicit system: ActorSystem) = {
    implicit def sslContext: SSLContext = {
      client.endpoint.config.settings.sslContext match {
        case Some(context) => context
        case None          => SSLContext.getDefault
      }
    }

    implicit val myClientEngineProvider = ClientSSLEngineProvider { engine =>
      engine
    }
    val uri = Uri(client.endpoint.uri)
    val host = uri.authority.host.toString
    val port = if (uri.effectivePort == 0) 80 else uri.effectivePort
    val isSecure = uri.scheme.toLowerCase.equals("https")
    val defaultHostConnectorSetup = Http.HostConnectorSetup(host, port, isSecure)
    val clientConnectionSettings = client.endpoint.config.settings.hostSettings.connectionSettings.copy(requestTimeout = reqSettings.timeout)
    val hostSettings = client.endpoint.config.settings.hostSettings.copy(connectionSettings = clientConnectionSettings)
    defaultHostConnectorSetup.copy(settings = Some(hostSettings), connectionType = client.endpoint.config.settings.connectionType)
  }

  def invokeToHttpResponseWithoutSetup(client: HttpClient,
                                       reqSettings: RequestSettings = Configuration.defaultRequestSettings,
                                       actorRef: ActorRef)
                                      (implicit system: ActorSystem): Try[(HttpRequest => Future[HttpResponse])] = {
    implicit val ec = system.dispatcher
    val pipeConfig = client.endpoint.config.pipeline.getOrElse(SimplePipelineConfig.empty)
    implicit val timeout: Timeout = client.endpoint.config.settings.hostSettings.connectionSettings.connectingTimeout.toMillis
    val pipeline = spray.client.pipelining.sendReceive(actorRef)
    val updatedPipeConfig = reqSettings.headers.isEmpty match {
      case false  =>
        val requestPipelines = pipeConfig.reqPipe ++ reqSettings.headers.map(new RequestUpdateHeaderHandler(_))
        pipeConfig.copy(reqPipe = requestPipelines)
      case true => pipeConfig
    }
		val pipelineActor = system.actorOf(Props(classOf[HttpClientPipelineActor], client.endpoint, updatedPipeConfig, pipeline))
    Try{
      client.status match {
        case Status.DOWN =>
          throw HttpClientMarkDownException(client.name, client.env)
        case _ =>
					import scala.concurrent.duration._
					implicit val timeout = Timeout(3 seconds)
					r: HttpRequest => (pipelineActor ? r).mapTo[HttpResponse]
      }
    }
  }

  implicit def endpointToUri(endpoint: Endpoint): String = {
    endpoint.uri
  }
}
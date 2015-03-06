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

import org.squbs.proxy.SimplePipeLineConfig
import spray.httpx.{UnsuccessfulResponseException, PipelineException}
import spray.client.pipelining._
import akka.actor.{Props, ActorRef, ActorSystem}
import spray.httpx.unmarshalling._
import scala.concurrent.{Await, Future}
import org.squbs.httpclient._
import spray.http.{Uri, HttpRequest, HttpResponse}
import scala.util.Try
import akka.pattern._
import akka.util.Timeout
import spray.can.Http
import akka.io.IO
import javax.net.ssl.SSLContext
import spray.io.ClientSSLEngineProvider
import org.squbs.httpclient.endpoint.Endpoint
import org.slf4j.LoggerFactory

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

  private def pipelining(client: Client)(implicit system: ActorSystem) = {
    implicit val ec = system.dispatcher
    implicit val connectionTimeout: Timeout = client.endpoint.config.hostSettings.connectionSettings.connectingTimeout.toMillis
    for (
      Http.HostConnectorInfo(connector, _) <-
      IO(Http) ? hostConnectorSetup(client)
    ) yield sendReceive(connector)
  }

  def hostConnectorSetup(client: Client)(implicit system: ActorSystem) = {
    implicit def sslContext: SSLContext = {
      client.endpoint.config.sslContext match {
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
    defaultHostConnectorSetup.copy(settings = Some(client.endpoint.config.hostSettings), connectionType = client.endpoint.config.connectionType)
  }

  def invokeToHttpResponse(client: Client)(implicit system: ActorSystem): Try[(HttpRequest => Future[HttpResponse])] = {
    implicit val ec = system.dispatcher
    val pipeConfig = client.endpoint.config.pipeline.getOrElse(SimplePipeLineConfig.empty)
    val connTimeout = client.endpoint.config.hostSettings.connectionSettings.connectingTimeout
    Try{
      val futurePipeline = pipelining(client)
      val pipeline = Await.result(futurePipeline, connTimeout)
			val pipelineActor = system.actorOf(Props(classOf[HttpClientPipeLineActor], pipeConfig, pipeline))
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

  def invokeToHttpResponseWithoutSetup(client: Client, actorRef: ActorRef)(implicit system: ActorSystem): Try[(HttpRequest => Future[HttpResponse])] = {
    implicit val ec = system.dispatcher
    val pipeConfig = client.endpoint.config.pipeline.getOrElse(SimplePipeLineConfig.empty)
    implicit val timeout: Timeout = client.endpoint.config.hostSettings.connectionSettings.connectingTimeout.toMillis
    val pipeline = spray.client.pipelining.sendReceive(actorRef)
		val pipelineActor = system.actorOf(Props(classOf[HttpClientPipeLineActor], pipeConfig, pipeline))
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
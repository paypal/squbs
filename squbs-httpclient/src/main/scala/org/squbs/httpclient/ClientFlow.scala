/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.httpclient

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.{ConnectionContext, HttpsConnectionContext, Http}
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, Keep, Flow}
import com.typesafe.config.Config
import org.squbs.endpoint.EndpointResolverRegistry
import org.squbs.env.{EnvironmentRegistry, Default, Environment}
import org.squbs.pipeline.streaming.{Context, RequestContext, PipelineExtension}

import scala.util.{Failure, Try}

object ClientFlow {

  val AkkaHttpClientCustomContext = "akka-http-client-custom-context"
  type ClientConnectionFlow[T] = Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]

  def apply[T](name: String,
              connectionContext: Option[HttpsConnectionContext] = None,
              settings: Option[ConnectionPoolSettings] = None,
              env: Environment = Default)(implicit system: ActorSystem, fm: Materializer): ClientConnectionFlow[T] = {

    // Check HttpClientManager if we already have initialized this  -- why?  Why not directly call cacedHostConnectionPool and let it deal..
    // we might need to do some statistics updates though for JMX

    val environment = env match {
      case Default => EnvironmentRegistry(system).resolve
      case _ => env
    }

    val endpoint = EndpointResolverRegistry(system).resolve(name, environment) getOrElse {
      throw HttpClientEndpointNotExistException(name, environment)
    }

    val clientConnectionFlow =
      if (endpoint.uri.getScheme == "https") {
        val httpsConnectionContext = connectionContext orElse {
          endpoint.sslContext map { sc => ConnectionContext.https(sc) }
        } getOrElse Http().defaultClientHttpsContext

        Http().cachedHostConnectionPoolHttps[RequestContext](endpoint.uri.getHost,
          endpoint.uri.getPort,
          httpsConnectionContext,
          connectionPoolSettings(name, system.settings.config, settings))
      } else {
        Http().cachedHostConnectionPool[RequestContext](endpoint.uri.getHost,
          endpoint.uri.getPort,
          connectionPoolSettings(name, system.settings.config, settings))
      }

    withPipeline[T](name, system.settings.config, clientConnectionFlow)

    // If Https, get SSLContext..  Probably with the above step (endpoint resolver)    -- done
    // Check if `ConnectionPoolSettings` is passed in.                                 -- done
    // If not, build one from configuration.                                           -- done
    // If Configuration does not have at all, call the api with out the settings       -- n/a
    // Http().cachedHostConnectionPool with the above configuration,                   -- done
    // If https, actually call cachedHostconnectionPoolHttps                           -- done
    // Check the configuration if there is any pipeline setting                        -- done
    // If yes, wrap it with the bidi and return.                                       -- done
  }

  private[httpclient] def connectionPoolSettings(name: String, config: Config,
                                                 settings: Option[ConnectionPoolSettings]) = {

    // TODO how come this unicomplex utility is sneaking here?  Should not be visible..
    import org.squbs.unicomplex.ConfigUtil._
    val clientConfig = config.getOption[Config](name).filter(_.getOption[String]("type") == Some("squbs.httpclient")) map {
      _.withFallback(config)
    } getOrElse config

    settings getOrElse { ConnectionPoolSettings(clientConfig) }
  }

  private[httpclient] def withPipeline[T](name: String, config: Config,
                                          clientConnectionFlow: ClientConnectionFlow[RequestContext])
                                          (implicit system: ActorSystem): ClientConnectionFlow[T] = {

    import org.squbs.unicomplex.ConfigUtil._
    val pipelineName = config.getOption[String](s"$name.pipeline")
    val defaultFlowsOn = config.getOption[Boolean](s"$name.defaultPipelineOn")

    PipelineExtension(system).getFlow((pipelineName, defaultFlowsOn), Context(name)) match {
      case Some(pipeline) =>
        val tupleToRequestContext = Flow[(HttpRequest, T)].map { case (request, t) =>
          RequestContext(request, 0) ++ (AkkaHttpClientCustomContext -> t)
        }

        val fromRequestContextToTuple = Flow[RequestContext].map { rc =>
          ( rc.response.getOrElse(Failure(new RuntimeException("Empty HttpResponse in client Pipeline"))),
            rc.attribute[T](AkkaHttpClientCustomContext).get)
          // TODO What to do if `AkkaHttpClientCustomContext` somehow got deleted in the pipeline?
        }
        val clientConnectionFlowWithPipeline = pipeline.joinMat(pipelineAdapter(clientConnectionFlow))(Keep.right)

        Flow.fromGraph( GraphDSL.create(clientConnectionFlowWithPipeline) { implicit b =>
          clientConnectionFlowWithPipeline =>
          import GraphDSL.Implicits._

          val toRequestContext = b.add(tupleToRequestContext)
          val fromRequestContext = b.add(fromRequestContextToTuple)

          toRequestContext ~> clientConnectionFlowWithPipeline ~> fromRequestContext

          FlowShape(toRequestContext.in, fromRequestContext.out)
        })
      case None =>
        val customContextToRequestContext = Flow[(HttpRequest, T)].map { case (request, t) =>
          (request, RequestContext(request, 0) ++ (AkkaHttpClientCustomContext -> t))
        }

        val requestContextToCustomContext =
          Flow[(Try[HttpResponse], RequestContext)].map { case (tryHttpResponse, rc) =>
            (tryHttpResponse, rc.attribute[T](AkkaHttpClientCustomContext).get)
        }

        Flow.fromGraph( GraphDSL.create(clientConnectionFlow) { implicit b => clientConnectionFlow =>
          import GraphDSL.Implicits._

          val toRequestContext = b.add(customContextToRequestContext)
          val fromRequestContext = b.add(requestContextToCustomContext)

          toRequestContext ~> clientConnectionFlow ~> fromRequestContext

          FlowShape(toRequestContext.in, fromRequestContext.out)
        })
    }
  }

  def pipelineAdapter(clientConnectionFlow: ClientConnectionFlow[RequestContext]):
  Flow[RequestContext, RequestContext, HostConnectionPool] = {
    val fromRc = Flow[RequestContext].map { rc => (rc.request, rc) }
    val toRc = Flow[(Try[HttpResponse], RequestContext)].map {
      case (responseTry, rc) => rc.copy(response = Option(responseTry))
    }

    fromRc.viaMat(clientConnectionFlow)(Keep.right).via(toRc)
  }
}

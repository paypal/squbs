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

import java.lang.management.ManagementFactory
import java.util.Optional
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.http.javadsl.{model => jm}
import akka.http.org.squbs.util.JavaConverters._
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.{javadsl => jd}
import akka.japi.Pair
import akka.stream.scaladsl.{Flow, GraphDSL, Keep}
import akka.stream.{FlowShape, Materializer, javadsl => js}
import com.typesafe.config.Config
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.SSLConfigFactory
import org.squbs.resolver.ResolverRegistry
import org.squbs.env.{Default, Environment, EnvironmentResolverRegistry}
import org.squbs.pipeline.{ClientPipeline, Context, PipelineExtension, PipelineSetting, RequestContext}

import scala.util.{Failure, Try}

object ClientFlow {

  val AkkaHttpClientCustomContext = "akka-http-client-custom-context"
  type ClientConnectionFlow[T] = Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]

  def create[T](name: String, system: ActorSystem, mat: Materializer):
  js.Flow[Pair[jm.HttpRequest, T], Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] =
    toJava[T](apply[T](name)(system, mat))

  def create[T](name: String,
                connectionContext: Optional[jd.HttpsConnectionContext],
                settings: Optional[jd.settings.ConnectionPoolSettings],
                system: ActorSystem, mat: Materializer):
  js.Flow[Pair[jm.HttpRequest, T],Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] = {
    val (cCtx, sSettings) = fromJava(connectionContext, settings)
    toJava[T](apply[T](name, cCtx, sSettings)(system, mat))
  }

  def create[T](name: String,
                connectionContext: Optional[jd.HttpsConnectionContext],
                settings: Optional[jd.settings.ConnectionPoolSettings],
                env: Environment,
                system: ActorSystem, mat: Materializer):
  js.Flow[Pair[jm.HttpRequest, T],Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] = {
    val (cCtx, sSettings) = fromJava(connectionContext, settings)
    toJava[T](apply[T](name, cCtx, sSettings, env)(system, mat))
  }

  def apply[T](name: String,
              connectionContext: Option[HttpsConnectionContext] = None,
              settings: Option[ConnectionPoolSettings] = None,
              env: Environment = Default)(implicit system: ActorSystem, fm: Materializer): ClientConnectionFlow[T] = {

    val environment = env match {
      case Default => EnvironmentResolverRegistry(system).resolve
      case _ => env
    }

    val endpoint = ResolverRegistry(system).resolve[HttpEndpoint](name, environment) getOrElse {
      throw HttpClientEndpointNotExistException(name, environment)
    }

    val config = system.settings.config
    import org.squbs.util.ConfigUtil._
    val clientSpecificConfig = config.getOption[Config](name).filter {
      _.getOption[String]("type") contains "squbs.httpclient"
    }
    val clientConfigWithDefaults = clientSpecificConfig.map(_.withFallback(config)).getOrElse(config)
    val cps = settings.getOrElse(ConnectionPoolSettings(clientConfigWithDefaults))

    val clientConnectionFlow =
      if (endpoint.uri.getScheme == "https") {

        val akkaOverrides = clientConfigWithDefaults.getConfig("akka.ssl-config")
        val defaults = clientConfigWithDefaults.getConfig("ssl-config")
        val sslConfig = AkkaSSLConfig().withSettings(SSLConfigFactory.parse(akkaOverrides withFallback defaults))

        val httpsConnectionContext = connectionContext orElse {
          endpoint.asInstanceOf[HttpEndpoint].sslContext map { sc => ConnectionContext.https(sc, Some(sslConfig)) }
        } getOrElse Http().defaultClientHttpsContext

        Http().cachedHostConnectionPoolHttps[RequestContext](endpoint.uri.getHost, endpoint.uri.getPort,
          httpsConnectionContext, cps)
      } else {
        Http().cachedHostConnectionPool[RequestContext](endpoint.uri.getHost, endpoint.uri.getPort, cps)
      }

    val pipelineName = clientSpecificConfig.flatMap(_.getOption[String]("pipeline"))
    val defaultFlowsOn = clientSpecificConfig.flatMap(_.getOption[Boolean]("defaultPipeline"))

    val mBeanServer = ManagementFactory.getPlatformMBeanServer
    val beanName = new ObjectName(s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=$name")
    if(!mBeanServer.isRegistered(beanName)) mBeanServer.registerMBean(HttpClientConfigMXBeanImpl(name,
                                                                                                 endpoint.uri.toString,
                                                                                                 environment.name,
                                                                                                 pipelineName,
                                                                                                 defaultFlowsOn,
                                                                                                 cps), beanName)

    withPipeline[T](name, (pipelineName, defaultFlowsOn), clientConnectionFlow)
  }

  private[httpclient] def withPipeline[T](name: String, pipelineSetting: PipelineSetting,
                                          clientConnectionFlow: ClientConnectionFlow[RequestContext])
                                          (implicit system: ActorSystem): ClientConnectionFlow[T] = {

    PipelineExtension(system).getFlow(pipelineSetting, Context(name, ClientPipeline)) match {
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

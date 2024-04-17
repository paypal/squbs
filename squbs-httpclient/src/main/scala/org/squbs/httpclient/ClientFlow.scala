/*
 * Copyright 2017 PayPal
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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.Logging
import org.apache.pekko.http.javadsl.{model => jm}
import org.apache.pekko.http.org.squbs.util.JavaConverters._
import org.apache.pekko.http.scaladsl.Http.HostConnectionPool
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.settings.{ConnectionPoolSettings, HttpsProxySettings}
import org.apache.pekko.http.scaladsl.{ClientTransport, ConnectionContext, Http, HttpsConnectionContext}
import org.apache.pekko.http.{javadsl => jd}
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream.scaladsl.{Flow, GraphDSL, Keep}
import org.apache.pekko.stream.{FlowShape, Materializer, javadsl => js}
import com.typesafe.config.{Config, ConfigFactory}
import org.squbs.env.{Default, Environment, EnvironmentResolverRegistry}
import org.squbs.pipeline.{ClientPipeline, Context, PipelineExtension, PipelineSetting, RequestContext}
import org.squbs.resolver.ResolverRegistry
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState
import org.squbs.streams.circuitbreaker.{CircuitBreaker, CircuitBreakerSettings, japi}
import org.squbs.util.ConfigUtil._

import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.management.ObjectName
import scala.compat.java8.FunctionConverters._
import scala.compat.java8.OptionConverters
import scala.util.{Failure, Success, Try}

object ClientFlow {

  val PekkoHttpClientCustomContext = "pekko-http-client-custom-context"
  type ClientConnectionFlow[T] = Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]
  private[httpclient] val defaultResolverRegistrationRecord = new ConcurrentHashMap[String, Unit]

  /**
    * Java API
    *
    * Helps to create a [[ClientConnectionFlow]] using builder pattern.
    */
  def builder[T](): Builder[T] = Builder[T]()

  /**
    * Java API
    */
  case class Builder[T](
    connectionContext: Optional[jd.HttpsConnectionContext] = Optional.empty(),
    settings: Optional[jd.settings.ConnectionPoolSettings] = Optional.empty(),
    circuitBreakerSettings: Optional[japi.CircuitBreakerSettings[jm.HttpRequest, jm.HttpResponse, T]] =
      Optional.empty[japi.CircuitBreakerSettings[jm.HttpRequest, jm.HttpResponse, T]](),
    environment: Environment = Default) {

    def withConnectionContext(connectionContext: jd.HttpsConnectionContext): Builder[T] =
      copy(connectionContext = Optional.of(connectionContext))

    def withSettings(settings: jd.settings.ConnectionPoolSettings): Builder[T] =
      copy(settings = Optional.of(settings))

    def withCircuitBreakerSettings(
      circuitBreakerSettings: japi.CircuitBreakerSettings[jm.HttpRequest, jm.HttpResponse, T]): Builder[T] =
      copy(circuitBreakerSettings = Optional.of(circuitBreakerSettings))

    def withEnvironment(environment: Environment): Builder[T] = copy(environment = environment)

    def create(name: String, system: ActorSystem, mat: Materializer):
        js.Flow[Pair[jm.HttpRequest, T], Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] =
      ClientFlow.create(name, connectionContext, settings, circuitBreakerSettings, environment, system, mat)
  }

  /**
    * Java API
    *
    * Creates a [[ClientConnectionFlow]] from client specific configuration specified in application.conf falling
    * back to the default settings.
    */
  def create[T](name: String, system: ActorSystem, mat: Materializer):
  js.Flow[Pair[jm.HttpRequest, T], Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] =
    toJava[T](apply[T](name)(system, mat))

  /**
    * Java API
    *
    * Creates a [[ClientConnectionFlow]] from provided settings
    */
  def create[T](name: String,
                connectionContext: Optional[jd.HttpsConnectionContext],
                settings: Optional[jd.settings.ConnectionPoolSettings],
                circuitBreakerSettings: Optional[japi.CircuitBreakerSettings[jm.HttpRequest, jm.HttpResponse, T]],
                env: Environment,
                system: ActorSystem, mat: Materializer):
  js.Flow[Pair[jm.HttpRequest, T],Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] = {
    val (cCtx, sSettings) = fromJava(connectionContext, settings)
    toJava[T](apply[T](name, cCtx, sSettings, toScala(circuitBreakerSettings), env)(system, mat))
  }

  private def toScala[T](
      circuitBreakerSettings: Optional[japi.CircuitBreakerSettings[jm.HttpRequest, jm.HttpResponse, T]]) = {
    OptionConverters.toScala(circuitBreakerSettings).map(_.toScala).map { sCircuitBreakerSettings =>
      CircuitBreakerSettings[HttpRequest, HttpResponse, T](sCircuitBreakerSettings.circuitBreakerState)
        .copy(fallback = sCircuitBreakerSettings.fallback
          .map(f => (httpRequest: HttpRequest) => f(httpRequest).map(_.asInstanceOf[HttpResponse])))
        .copy(failureDecider = sCircuitBreakerSettings.failureDecider
          .map(f => (httpResponse: Try[HttpResponse]) => f(httpResponse)))
        .copy(uniqueIdMapper = sCircuitBreakerSettings.uniqueIdMapper)
    }
  }

  def apply[T](name: String,
              connectionContext: Option[HttpsConnectionContext] = None,
              settings: Option[ConnectionPoolSettings] = None,
              circuitBreakerSettings: Option[CircuitBreakerSettings[HttpRequest, HttpResponse, T]] = None,
              env: Environment = Default)(implicit system: ActorSystem, fm: Materializer): ClientConnectionFlow[T] = {

    defaultResolverRegistrationRecord.computeIfAbsent(
      system.name,
      asJavaFunction((_: String) => ResolverRegistry(system).register[HttpEndpoint](new DefaultHttpEndpointResolver))
      )

    val environment = env match {
      case Default => EnvironmentResolverRegistry(system).resolve
      case _ => env
    }

    val endpoint = ResolverRegistry(system).resolve[HttpEndpoint](name, environment) getOrElse {
      throw HttpClientEndpointNotExistException(name, environment)
    }

    // The resolver may also contribute to the configuration and if it does, it has higher priority over the defaults.
    val config = endpoint.config.map(_.withFallback(system.settings.config)).getOrElse(system.settings.config)
    val clientSpecificConfig = config.getOption[Config](s""""$name"""").filter {
      _.getOption[String]("type") contains "squbs.httpclient"
    }
    val clientConfigWithDefaults = clientSpecificConfig.map(_.withFallback(config)).getOrElse(config)
    val cps = settings.getOrElse {
      val log = Logging(system, this.getClass)
      Try { HttpsProxySettings(clientConfigWithDefaults) } match {
        case Success(proxySettings) =>
          log.info("Successfully loaded proxy settings: {}", proxySettings)
          ConnectionPoolSettings(clientConfigWithDefaults).withTransport(ClientTransport.httpsProxy(
              InetSocketAddress.createUnresolved(proxySettings.host, proxySettings.port)))
        case Failure(e) =>
          log.error(e, "Did not load proxy settings. Falling back to non-proxy.")
          ConnectionPoolSettings(clientConfigWithDefaults)
      }
    }

    val endpointPort = endpoint.uri.effectivePort
    val clientConnectionFlow =
      if (endpoint.uri.scheme == "https") {
        val httpsConnectionContext = connectionContext orElse {
          endpoint.sslContext map { sc =>
            endpoint.sslEngineProvider match {
              case Some(provider) =>
                ConnectionContext.httpsClient { (host, port) => provider.createSSLEngine(sc, host, port) }
              case None =>
                ConnectionContext.httpsClient(sc)
            }
          }
        } getOrElse Http().defaultClientHttpsContext

        Http().cachedHostConnectionPoolHttps[RequestContext](endpoint.uri.authority.host.address,
          endpointPort, httpsConnectionContext, cps)
      } else {
        Http().cachedHostConnectionPool[RequestContext](endpoint.uri.authority.host.address,
          endpointPort, cps)
      }

    val enableCircuitBreaker =
      circuitBreakerSettings.isDefined || clientSpecificConfig.exists(_.hasPath("circuit-breaker"))

    val circuitBreakerStateName =
      if(enableCircuitBreaker) circuitBreakerSettings.map(_.circuitBreakerState.name).getOrElse(s"$name-httpclient")
      else "N/A"

    val pipelineName = clientSpecificConfig.flatMap(_.getOption[String]("pipeline"))
    val defaultFlowsOn = clientSpecificConfig.flatMap(_.getOption[Boolean]("defaultPipeline"))
    val pipelineSetting = (pipelineName, defaultFlowsOn)

    val mBeanServer = ManagementFactory.getPlatformMBeanServer
    val beanName = new ObjectName(
      s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=${ObjectName.quote(name)}")
    if(!mBeanServer.isRegistered(beanName)) mBeanServer.registerMBean(HttpClientConfigMXBeanImpl(
      name,
      endpoint.uri.toString,
      endpointPort,
      environment.name,
      pipelineName,
      defaultFlowsOn,
      circuitBreakerStateName,
      cps), beanName)

    if(enableCircuitBreaker)
      withPipeline[T](name, pipelineSetting,
        withCircuitBreaker[T](name, clientSpecificConfig, circuitBreakerSettings, clientConnectionFlow))
    else
      withPipeline[T](name, pipelineSetting, clientConnectionFlow)
  }

  private[httpclient] def withCircuitBreaker[T](
    name: String,
    clientSpecificConfig: Option[Config],
    circuitBreakerSettings: Option[CircuitBreakerSettings[HttpRequest, HttpResponse, T]],
    clientConnectionFlow: ClientConnectionFlow[RequestContext])
    (implicit system: ActorSystem, fm: Materializer):
  ClientConnectionFlow[RequestContext] = {

    val cbs =
      circuitBreakerSettings.collect {
        case circuitBreakerSettings@CircuitBreakerSettings(_, _, _, None, _) =>
          circuitBreakerSettings.withFailureDecider(
            tryHttpResponse => tryHttpResponse.isFailure || tryHttpResponse.get.status.isFailure)
        case circuitBreakerSettings => circuitBreakerSettings
      }.getOrElse {
        val clientSpecificCircuitBreakerConfig =
        clientSpecificConfig
          .flatMap(_.getOption[Config]("circuit-breaker"))
          .getOrElse(ConfigFactory.empty)
        CircuitBreakerSettings[HttpRequest, HttpResponse, T](
          AtomicCircuitBreakerState(s"$name-httpclient", clientSpecificCircuitBreakerConfig))
          .withFailureDecider(tryHttpResponse => tryHttpResponse.isFailure || tryHttpResponse.get.status.isFailure)
          .withCleanUp(response => response.discardEntityBytes())
      }

    // Map CircuitBreakerSettings[HttpRequest, HttpResponse, T] to CircuitBreakerSettings[HttpRequest, Try[HttpResponse], RequestContext]
    val clientFlowCircuitBreakerSettings =
      CircuitBreakerSettings[HttpRequest, Try[HttpResponse], RequestContext](cbs.circuitBreakerState)
        .copy(fallback = cbs.fallback.map(f => (httpRequest: HttpRequest) => Try(f(httpRequest))))
        .copy(failureDecider =
          cbs.failureDecider
            .map(f => (tryTryHttpResponse: Try[Try[HttpResponse]]) => tryTryHttpResponse match {
              case Success(tryHttpResponse) => f(tryHttpResponse)
              case _ => true
            }))
          .copy(uniqueIdMapper =
            cbs.uniqueIdMapper.map(f => (rc: RequestContext) => f(rc.attribute[T](PekkoHttpClientCustomContext).get)))
        .withCleanUp(tryHttpResponse => tryHttpResponse.foreach(cbs.cleanUp))

    val circuitBreakerBidiFlow = CircuitBreaker(clientFlowCircuitBreakerSettings)

    circuitBreakerBidiFlow
      .joinMat(clientConnectionFlow)(Keep.right)
      .map { case(tryTryHttpResponse, requestContext) => tryTryHttpResponse.flatten -> requestContext }
  }

  private[httpclient] def withPipeline[T](name: String, pipelineSetting: PipelineSetting,
                                          clientConnectionFlow: ClientConnectionFlow[RequestContext])
                                          (implicit system: ActorSystem): ClientConnectionFlow[T] = {

    PipelineExtension(system).getFlow(pipelineSetting, Context(name, ClientPipeline)) match {
      case Some(pipeline) =>
        val tupleToRequestContext = Flow[(HttpRequest, T)].map { case (request, t) =>
          RequestContext(request, 0).withAttribute(PekkoHttpClientCustomContext, t)
        }

        val fromRequestContextToTuple = Flow[RequestContext].map { rc =>
          ( rc.response.getOrElse(Failure(new RuntimeException("Empty HttpResponse in client Pipeline"))),
            rc.attribute[T](PekkoHttpClientCustomContext).get)
          // TODO What to do if `AkkaHttpClientCustomContext` somehow got deleted in the pipeline?
        }
        val clientConnectionFlowWithPipeline = pipeline.joinMat(pipelineAdapter(clientConnectionFlow))(Keep.right)

        Flow.fromGraph( GraphDSL.createGraph(clientConnectionFlowWithPipeline) { implicit b =>
          clientConnectionFlowWithPipeline =>
          import GraphDSL.Implicits._

          val toRequestContext = b.add(tupleToRequestContext)
          val fromRequestContext = b.add(fromRequestContextToTuple)

          toRequestContext ~> clientConnectionFlowWithPipeline ~> fromRequestContext

          FlowShape(toRequestContext.in, fromRequestContext.out)
        })
      case None =>
        val customContextToRequestContext = Flow[(HttpRequest, T)].map { case (request, t) =>
          (request, RequestContext(request, 0).withAttribute(PekkoHttpClientCustomContext, t))
        }

        val requestContextToCustomContext =
          Flow[(Try[HttpResponse], RequestContext)].map { case (tryHttpResponse, rc) =>
            (tryHttpResponse, rc.attribute[T](PekkoHttpClientCustomContext).get)
        }

        Flow.fromGraph( GraphDSL.createGraph(clientConnectionFlow) { implicit b => clientConnectionFlow =>
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

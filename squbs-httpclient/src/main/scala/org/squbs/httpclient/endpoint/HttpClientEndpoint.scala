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

package org.squbs.httpclient.endpoint

import java.util.Optional

import akka.actor._
import org.squbs.pipeline.{PipelineManager, PipelineSetting}
import spray.http.{HttpResponse, Uri}

import scala.collection.mutable.ListBuffer
import org.squbs.httpclient.env.{Default, Environment}
import org.squbs.httpclient.{CircuitBreakerSettings, Settings, Configuration}
import com.typesafe.scalalogging.LazyLogging

case class Endpoint(uri: Uri, config: Configuration) {
    val host = uri.authority.host.toString
    def port = if (uri.effectivePort == 0) 80 else uri.effectivePort
    val isSecure = uri.scheme.toLowerCase.equals("https")

  def withSettings(settings: Settings): Endpoint = copy(config = config.copy(settings = settings))

  def withCircuitBreakerSettings(circuitBreakerSettings: CircuitBreakerSettings): Endpoint =
    copy(config = config.copy(settings = config.settings.copy(circuitBreakerConfig = circuitBreakerSettings)))

  def withFallbackResponse(fallbackResponse: Option[HttpResponse]): Endpoint =
    copy(config = config.copy(settings = config.settings.copy(circuitBreakerConfig =
      config.settings.circuitBreakerConfig.copy(fallbackHttpResponse = fallbackResponse))))

  /**
   * Java API.
   * @param fallbackResponse The response to fallback to.
   * @return A new endpoint configured with the fallback response.
   */
  def withFallbackResponse(fallbackResponse: Optional[HttpResponse]): Endpoint = {
    import scala.compat.java8.OptionConverters._
    withFallbackResponse(fallbackResponse.asScala)
  }
}

object Endpoint {

  def check(endpoint: String) = {
    require(endpoint.toLowerCase.startsWith("http://") || endpoint.toLowerCase.startsWith("https://"),
      "service should be started with http:// or https://")
  }

  def apply(uri: Uri)(implicit system: ActorSystem): Endpoint = new Endpoint(uri, Configuration())
}

trait EndpointResolver {

  def name: String

  def resolve(svcName: String, env: Environment = Default): Option[Endpoint]
}

abstract class PipelineAwareEndpointResolver(system: ActorSystem) extends EndpointResolver {

  protected def getPipelineSetting(name: String): Option[PipelineSetting] = PipelineManager(system).getPipelineSetting(name)(system)

}

class EndpointRegistryExtension(system: ExtendedActorSystem) extends Extension with LazyLogging {
  val endpointResolvers = ListBuffer[EndpointResolver]()

  def register(resolver: EndpointResolver) = {
    endpointResolvers.find(_.name == resolver.name) match {
      case None =>
        endpointResolvers.prepend(resolver)
      case Some(routing) =>
        logger.warn(s"Endpoint Resolver: ${resolver.name} already registered, skipped!")
    }
  }

  def unregister(name: String) = {
    endpointResolvers.find(_.name == name) match {
      case None =>
        logger.warn("Endpoint Resolver:" + name + " cannot be found, skipped unregister!")
      case Some(resolver) =>
        endpointResolvers.remove(endpointResolvers.indexOf(resolver))
    }
  }

  def route(svcName: String, env: Environment = Default): Option[EndpointResolver] = {
    endpointResolvers.find(_.resolve(svcName, env) != None)
  }

  def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    val resolvedEndpoint = endpointResolvers.foldLeft[Option[Endpoint]](None) {
      (endpoint: Option[Endpoint], resolver: EndpointResolver) =>
        endpoint match {
          case Some(_) =>
            endpoint
          case None =>
            resolver.resolve(svcName, env)
        }
    }
    resolvedEndpoint match {
      case Some(ep) =>
        logger.debug(s"Endpoint can be resolved by ($svcName, $env), the endpoint uri is:" + ep.uri)
        resolvedEndpoint
      case None if svcName != null && (svcName.startsWith("http://") || svcName.startsWith("https://")) =>
        logger.debug(s"Endpoint can be resolved with service name match http:// or https:// pattern by " +
          s"($svcName, $env), the endpoint uri is:" + svcName)
        Some(Endpoint(svcName)(system))
      case _ =>
        logger.warn(s"Endpoint can not be resolved by ($svcName, $env)!")
        None
    }
  }
}

object EndpointRegistry extends ExtensionId[EndpointRegistryExtension] with ExtensionIdProvider {

  override def lookup() = EndpointRegistry

  override def createExtension(system: ExtendedActorSystem): EndpointRegistryExtension =
    new EndpointRegistryExtension(system)

  override def get(system: ActorSystem): EndpointRegistryExtension = super.get(system)
}

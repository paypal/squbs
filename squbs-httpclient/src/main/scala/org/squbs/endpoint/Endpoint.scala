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

package org.squbs.endpoint

import java.net.URI
import javax.net.ssl.SSLContext

import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.env.{Default, Environment}

import scala.collection.mutable.ListBuffer

// TODO Endpoint can be used by non-http clients as well, e.g., Kafka, db, etc.. So, using javax.net.URI
case class Endpoint(uri: URI, sslContext: Option[SSLContext] = None)

object Endpoint {
  def apply(s: String): Endpoint = Endpoint(new URI(s))

  /**
    * Java API
    */
  def create(s: String): Endpoint = Endpoint(new URI(s))
}

trait EndpointResolver {
  def name: String
  def resolve(svcName: String, env: Environment = Default): Option[Endpoint]
}

class EndpointResolverRegistryExtension(system: ExtendedActorSystem) extends Extension with LazyLogging {
  val endpointResolvers = ListBuffer[EndpointResolver]()

  def register(resolver: EndpointResolver) = {
    endpointResolvers.find(_.name == resolver.name) match {
      case None => endpointResolvers.prepend(resolver)
      case Some(routing) => logger.warn(s"Endpoint Resolver: ${resolver.name} already registered, skipped!")
    }
  }

  def unregister(name: String) = {
    endpointResolvers.find(_.name == name) match {
      case None => logger.warn("Endpoint Resolver: {} cannot be found, skipped unregister!", name)
      case Some(resolver) => endpointResolvers -= resolver
    }
  }

  def route(svcName: String, env: Environment = Default): Option[EndpointResolver] = {
    endpointResolvers.find(_.resolve(svcName, env) != None)
  }

  def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    val resolvedEndpoint = endpointResolvers.foldLeft[Option[Endpoint]](None) {
      (endpoint: Option[Endpoint], resolver: EndpointResolver) =>
        endpoint match {
          case Some(_) => endpoint
          case None => resolver.resolve(svcName, env)
        }
    }
    resolvedEndpoint match {
      case Some(ep) =>
        logger.debug(s"Endpoint can be resolved by ($svcName, $env), the endpoint uri is:" + ep.uri)
        resolvedEndpoint
      case _ =>
        logger.warn(s"Endpoint can not be resolved by ($svcName, $env)!")
        None
    }
  }
}

object EndpointResolverRegistry extends ExtensionId[EndpointResolverRegistryExtension] with ExtensionIdProvider {

  override def lookup() = EndpointResolverRegistry

  override def createExtension(system: ExtendedActorSystem): EndpointResolverRegistryExtension =
    new EndpointResolverRegistryExtension(system)

  /**
    * Java API
    */
  override def get(system: ActorSystem): EndpointResolverRegistryExtension = super.get(system)
}

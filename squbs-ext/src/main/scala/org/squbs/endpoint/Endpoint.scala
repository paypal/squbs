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

import java.beans.ConstructorProperties
import java.lang.management.ManagementFactory
import java.net.URI
import java.util.Optional
import java.util.function.BiFunction
import javax.management.{MXBean, ObjectName}
import javax.net.ssl.SSLContext

import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.env.{Default, Environment}

import scala.beans.BeanProperty

case class Endpoint(uri: URI, sslContext: Option[SSLContext] = None)

object Endpoint {
  def apply(s: String): Endpoint = Endpoint(new URI(s))

  def apply(s: String, sslContext: Option[SSLContext]): Endpoint = Endpoint(new URI(s), sslContext)

  /**
    * Java API
    */
  def create(s: String): Endpoint = Endpoint(new URI(s))

  /**
    * Java API
    */
  import scala.compat.java8.OptionConverters._
  def create(s: String, sslContext: Optional[SSLContext]): Endpoint = Endpoint(new URI(s), sslContext.asScala)
}

/**
  * Scala API for implementing an EndpointResolver.
  */
trait EndpointResolver {
  def name: String
  def resolve(svcName: String, env: Environment = Default): Option[Endpoint]
}

/**
  * Java API for implementing an EndpointResolver.
  */
abstract class AbstractEndpointResolver {
  def name: String
  def resolve(svcName: String, env: Environment): Optional[Endpoint]
  def resolve(svcName: String): Optional[Endpoint] = resolve(svcName, Default)

  private[endpoint] final def toEndpointResolver = new EndpointResolver {
    def name: String = AbstractEndpointResolver.this.name
    def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
      import scala.compat.java8.OptionConverters._
      AbstractEndpointResolver.this.resolve(svcName, env).asScala
    }
  }
}

class EndpointResolverRegistryExtension(system: ExtendedActorSystem) extends Extension with LazyLogging {

  private[endpoint] var endpointResolvers = List.empty[EndpointResolver]

  /**
    * Scala API to register a resolver.
    * @param resolver The resolver implementation
    */
  def register(resolver: EndpointResolver): Unit =
    endpointResolvers.find(_.name == resolver.name) match {
      case None => endpointResolvers = resolver :: endpointResolvers
      case Some(_) => logger.warn(s"Endpoint Resolver: ${resolver.name} already registered, skipped!")
    }

  /**
    * Scala API to register a resolver on the fly.
    * @param name The resolver name
    * @param resolve The resolve function
    */
  def register(name: String, resolve: (String, Environment) => Option[Endpoint]): Unit = {
    val rName = name
    val resolveFn = resolve
    val resolver = new EndpointResolver {
      def name: String = rName
      def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = resolveFn(svcName, env)
    }
    register(resolver)
  }

  /**
    * Java API to register a resolver.
    * @param resolver The resolver implementation
    */
  def register(resolver: AbstractEndpointResolver): Unit = this.register(resolver.toEndpointResolver)

  /**
    * Java API to register a resolver on the fly.
    * @param name The resolver name
    * @param resolve The resolve closure
    */
  def register(name: String, resolve: BiFunction[String, Environment, Optional[Endpoint]]): Unit = {
    val rName = name
    val resolveFn = resolve
    val resolver = new AbstractEndpointResolver {
      def name: String = rName
      def resolve(svcName: String, env: Environment): Optional[Endpoint] = resolveFn.apply(svcName, env)
    }
    register(resolver)
  }

  def unregister(name: String) {
    val originalLength = endpointResolvers.length
    endpointResolvers = endpointResolvers.filterNot(_.name == name)
    if(endpointResolvers.length == originalLength)
      logger.warn("Endpoint Resolver: {} cannot be found, skipped unregister!", name)
  }

  def route(svcName: String, env: Environment = Default): Option[EndpointResolver] = {
    endpointResolvers.find(_.resolve(svcName, env).isDefined)
  }

  def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    val resolvedEndpoint = endpointResolvers.view.map(_.resolve(svcName, env)).collectFirst {
      case Some(endpoint) => endpoint
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

  override def createExtension(system: ExtendedActorSystem): EndpointResolverRegistryExtension = {
    val mBeanServer = ManagementFactory.getPlatformMBeanServer
    val beanName = new ObjectName(s"org.squbs.configuration.${system.name}:type=EndpointResolverRegistry")
    if (!mBeanServer.isRegistered(beanName))
      mBeanServer.registerMBean(EndpointResolverRegistryMXBeanImpl(system), beanName)
    new EndpointResolverRegistryExtension(system)
  }

  /**
    * Java API
    */
  override def get(system: ActorSystem): EndpointResolverRegistryExtension = super.get(system)
}


// $COVERAGE-OFF$
case class EndpointResolverInfo @ConstructorProperties(
  Array("position", "name", "className"))(@BeanProperty position: Int,
                                          @BeanProperty name: String,
                                          @BeanProperty className: String)

// $COVERAGE-ON$

@MXBean
trait EndpointResolverRegistryMXBean {
  def getEndpointResolverInfo: java.util.List[EndpointResolverInfo]
}

case class EndpointResolverRegistryMXBeanImpl(system: ActorSystem) extends EndpointResolverRegistryMXBean {

  override def getEndpointResolverInfo: java.util.List[EndpointResolverInfo] = {
    import scala.collection.JavaConversions._
    EndpointResolverRegistry(system).endpointResolvers.zipWithIndex map { case (resolver, position) =>
      EndpointResolverInfo(position, resolver.name, resolver.getClass.getName)
    }
  }
}

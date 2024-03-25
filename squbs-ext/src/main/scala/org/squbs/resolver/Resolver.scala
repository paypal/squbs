/*
 *  Copyright 2017 PayPal
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

package org.squbs.resolver

import java.beans.ConstructorProperties
import java.lang.management.ManagementFactory
import java.util.Optional
import java.util.function.BiFunction
import javax.management.{MXBean, ObjectName}

import org.apache.pekko.actor._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.env.{Default, Environment}

import scala.beans.BeanProperty
import scala.compat.java8.OptionConverters._
import scala.reflect._

case class EndpointNotExistsException(name: String, env: Environment) extends Exception {
  override def getMessage: String = s"Endpoint $name for environment $env not found."
}

/**
  * Scala API for implementing an EndpointResolver.
  */
trait Resolver[T] {
  def name: String
  def resolve(name: String, env: Environment = Default): Option[T]
}

/**
  * Java API for implementing a multi-type resolver.
  */
abstract class AbstractResolver[T] {
  def name: String
  def resolve(name: String, env: Environment): Optional[T]

  private[resolver] final def toEndpointResolver = new Resolver[T] {
    def name: String = AbstractResolver.this.name
    def resolve(name: String, env: Environment = Default): Option[T] = AbstractResolver.this.resolve(name, env).asScala
  }
}

class ResolverRegistryExtension(system: ExtendedActorSystem) extends Extension with LazyLogging {

  private[resolver] var resolvers = List.empty[(Class[_], Resolver[_])]

  /**
    * Scala API to register a resolver.
    *
    * @param resolver The resolver implementation
    */
  def register[T: ClassTag](resolver: Resolver[T]): Unit = {
    import scala.language.existentials
    resolvers.find { case (_, oldResolver) => oldResolver.name == resolver.name } match {
      case None => resolvers = (classTag[T].runtimeClass, resolver) :: resolvers
      case Some(_) => logger.warn(s"Endpoint Resolver: ${resolver.name} already registered, skipped!")
    }
  }

  /**
    * Scala API to register a resolver on the fly.
    *
    * @param name    The resolver name
    * @param resolve The resolve function
    */
  def register[T: ClassTag](name: String)(resolve: (String, Environment) => Option[T]): Unit = {
    val rName = name
    val resolveFn = resolve
    val resolver = new Resolver[T] {

      def name: String = rName

      def resolve(name: String, env: Environment = Default): Option[T] = resolveFn(name, env)
    }
    register[T](resolver)
  }

  /**
    * Java API to register a resolver.
    *
    * @param clazz The type to associate this resolver.
    * @param resolver The resolver implementation.
    */
  def register[T](clazz: Class[T], resolver: AbstractResolver[T]): Unit =
    register[T](resolver.toEndpointResolver)(ClassTag(clazz))

  /**
    * Java API to register a resolver on the fly.
    *
    * @param name    The resolver name
    * @param resolve The resolve closure
    */
  def register[T](clazz: Class[T], name: String, resolve: BiFunction[String, Environment, Optional[T]]): Unit = {
    val rName = name
    val resolveFn = resolve
    val resolver = new Resolver[T] {
      def name: String = rName

      def resolve(name: String, env: Environment): Option[T] = resolveFn.apply(name, env).asScala
    }
    register[T](resolver)(ClassTag(clazz))
  }

  def unregister(name: String): Unit = {
    val originalLength = resolvers.length
    resolvers = resolvers.filterNot { case (_, res) => res.name == name }
    if (resolvers.length == originalLength)
      logger.warn("Endpoint Resolver: {} cannot be found, skipped unregister!", name)
  }

  /**
    * Java API for test case validations.
    */
  private[resolver] def findResolver[T](clazz: Class[T], name: String, env: Environment): Optional[Resolver[T]] =
    findResolver[T](name, env)(ClassTag(clazz)).asJava


  /**
    * Scala API for test case validations.
    */
  private[resolver] def findResolver[T: ClassTag](name: String, env: Environment = Default): Option[Resolver[T]] =
    resolvers.find { case (clazz, res) =>
      if (clazz.isAssignableFrom(classTag[T].runtimeClass)) res.resolve(name, env).isDefined
      else false
    } map {
      _._2.asInstanceOf[Resolver[T]]
    }

  /**
    * Scala API. Resolves the resource of given type.
    * @param name The resource name.
    * @param env The environment.
    * @tparam T The type of the resource.
    * @return The resource, or None.
    */
  def resolve[T: ClassTag](name: String, env: Environment = Default): Option[T] = {
    val clazz = classTag[T].runtimeClass
    val resolvedEndpoint = resolvers.view map { case (regClass, res) =>
      if (regClass.isAssignableFrom(clazz)) res.resolve(name, env).asInstanceOf[Option[T]]
      else None
    } collectFirst {
      case Some(endpoint) if clazz.isAssignableFrom(endpoint.getClass) => endpoint.asInstanceOf[T]
    }

    resolvedEndpoint match {
      case Some(ep) =>
        logger.debug(s"Endpoint of type $clazz can be resolved by ($name, $env)," +
          s"the endpoint is:" + ep.toString)
        resolvedEndpoint
      case _ =>
        logger.warn(s"Endpoint of type $clazz can not be resolved by ($name, $env)!")
        None
    }
  }

  /**
    * Java API. Resolves the resource of given type.
    * @param clazz The class representing the resource type.
    * @param name The resource name.
    * @param env The environment.
    * @tparam T The type of the resource.
    * @return The resource, or None.
    */
  def resolve[T](clazz: Class[T], name: String, env: Environment): Optional[T] =
    resolve[T](name, env)(ClassTag(clazz)).asJava
}

object ResolverRegistry extends ExtensionId[ResolverRegistryExtension] with ExtensionIdProvider {

  override def lookup = ResolverRegistry

  override def createExtension(system: ExtendedActorSystem): ResolverRegistryExtension = {
    val mBeanServer = ManagementFactory.getPlatformMBeanServer
    val beanName = new ObjectName(s"org.squbs.configuration.${system.name}:type=ResolverRegistry")
    if (!mBeanServer.isRegistered(beanName))
      mBeanServer.registerMBean(ResolverRegistryMXBeanImpl(system), beanName)
    new ResolverRegistryExtension(system)
  }

  /**
    * Java API
    */
  override def get(system: ActorSystem): ResolverRegistryExtension = super.get(system)
}


// $COVERAGE-OFF$
case class ResolverInfo @ConstructorProperties(
  Array("position", "name", "className"))(@BeanProperty position: Int,
                                          @BeanProperty forType: String,
                                          @BeanProperty name: String,
                                          @BeanProperty className: String)

// $COVERAGE-ON$

@MXBean
trait ResolverRegistryMXBean {
  def getResolverInfo: java.util.List[ResolverInfo]
}

case class ResolverRegistryMXBeanImpl(system: ActorSystem) extends ResolverRegistryMXBean {

  override def getResolverInfo: java.util.List[ResolverInfo] = {
    import scala.jdk.CollectionConverters._
    ResolverRegistry(system).resolvers.zipWithIndex.map { case ((tpe, resolver), position) =>
      ResolverInfo(position, tpe.getName, resolver.name, resolver.getClass.getName)
    }.asJava
  }
}

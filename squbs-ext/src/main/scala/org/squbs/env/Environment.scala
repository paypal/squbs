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
package org.squbs.env

import java.beans.ConstructorProperties
import java.lang.management.ManagementFactory
import java.util
import javax.management.{ObjectName, MXBean}

import org.apache.pekko.actor._
import com.typesafe.scalalogging.LazyLogging

import scala.beans.BeanProperty

abstract class Environment {
  def name: String

  def lowercaseName: String = name.toLowerCase
}

case object Default extends Environment {
  override val name: String = "DEFAULT"

  /*
  java api
   */
  val value: Environment = this
}

case object QA extends Environment {
  override val name: String = "QA"
  /*
  java api
 */
  val value: Environment = this
}

case object DEV extends Environment {
  override val name: String = "DEV"
  /*
  java api
 */
  val value: Environment = this
}

case object PROD extends Environment {
  override val name: String = "PROD"
  /*
  java api
 */
  val value: Environment = this
}

case class RawEnv(name: String) extends Environment

trait EnvironmentResolver {
  def name: String

  def resolve: Environment
}

class EnvironmentResolverRegistryExtension(system: ExtendedActorSystem) extends Extension with LazyLogging {
  private[env] var environmentResolvers = List.empty[EnvironmentResolver]

  def register(resolver: EnvironmentResolver): Unit = {
    environmentResolvers.find(_.name == resolver.name) match {
      case None => environmentResolvers = resolver :: environmentResolvers
      case Some(oldResolver) =>
        logger.warn("Env Resolver:" + oldResolver.name + " already registered, skipped!")
    }
  }

  def unregister(name: String): Unit = {
    val originalLength = environmentResolvers.length
    environmentResolvers = environmentResolvers.filterNot(_.name == name)
    if(environmentResolvers.length == originalLength)
      logger.warn("Env Resolver:" + name + " cannot be found, skipping unregister!")
  }

  def resolve: Environment = {
    val resolvedEnv = environmentResolvers.view.map(_.resolve).collectFirst {
      case env if env != Default => env
    } getOrElse Default

    logger.debug(s"The environment is: " + resolvedEnv.lowercaseName)
    resolvedEnv
  }
}

object EnvironmentResolverRegistry extends ExtensionId[EnvironmentResolverRegistryExtension] with ExtensionIdProvider {

  override def lookup = EnvironmentResolverRegistry

  override def createExtension(system: ExtendedActorSystem): EnvironmentResolverRegistryExtension = {
    val mBeanServer = ManagementFactory.getPlatformMBeanServer
    val beanName = new ObjectName(s"org.squbs.configuration.${system.name}:type=EnvironmentResolverRegistry")
    if (!mBeanServer.isRegistered(beanName))
      mBeanServer.registerMBean(EnvironmentResolverRegistryMXBeanImpl(system), beanName)
    new EnvironmentResolverRegistryExtension(system)
  }

  override def get(system: ActorSystem): EnvironmentResolverRegistryExtension = super.get(system)
}

// $COVERAGE-OFF$
case class EnvironmentResolverInfo @ConstructorProperties(
  Array("position", "name", "className"))(@BeanProperty position: Int,
                                          @BeanProperty name: String,
                                          @BeanProperty className: String)

// $COVERAGE-ON$

@MXBean
trait EnvironmentResolverRegistryMXBean {
  def getEnvironmentResolverInfo: java.util.List[EnvironmentResolverInfo]
}

case class EnvironmentResolverRegistryMXBeanImpl(system: ActorSystem) extends EnvironmentResolverRegistryMXBean {

  override def getEnvironmentResolverInfo: util.List[EnvironmentResolverInfo] = {
    import scala.jdk.CollectionConverters._
    EnvironmentResolverRegistry(system).environmentResolvers.zipWithIndex.map { case(resolver, position) =>
      EnvironmentResolverInfo(position, resolver.name, resolver.getClass.getName)
    }.asJava
  }
}

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

package org.squbs.env

import akka.actor._
import com.typesafe.scalalogging.LazyLogging

abstract class Environment {
  def name: String

  def lowercaseName: String = name.toLowerCase
}

case object Default extends Environment {
  override val name: String = "DEFAULT"

  /*
  java api
   */
  val value = this
}

case object QA extends Environment {
  override val name: String = "QA"
  /*
  java api
 */
  val value = this
}

case object DEV extends Environment {
  override val name: String = "DEV"
  /*
  java api
 */
  val value = this
}

case object PROD extends Environment {
  override val name: String = "PROD"
  /*
  java api
 */
  val value = this
}

case class RawEnv(name: String) extends Environment

trait EnvironmentResolver {
  def name: String

  def resolve: Environment
}

class EnvironmentRegistryExtension(system: ExtendedActorSystem) extends Extension with LazyLogging {
  var environmentResolvers = List[EnvironmentResolver]()

  def register(resolver: EnvironmentResolver) {
    environmentResolvers.find(_.name == resolver.name) match {
      case None => environmentResolvers = resolver :: environmentResolvers
      case Some(oldResolver) =>
        logger.warn("Env Resolver:" + oldResolver.name + " already registered, skipped!")
    }
  }

  def unregister(name: String) {
    val originalLentgh = environmentResolvers.length
    environmentResolvers = environmentResolvers.filterNot(_.name == name)
    if(environmentResolvers.length == originalLentgh)
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

object EnvironmentRegistry extends ExtensionId[EnvironmentRegistryExtension] with ExtensionIdProvider {

  override def lookup() = EnvironmentRegistry

  override def createExtension(system: ExtendedActorSystem): EnvironmentRegistryExtension =
    new EnvironmentRegistryExtension(system)

  override def get(system: ActorSystem): EnvironmentRegistryExtension = super.get(system)
}


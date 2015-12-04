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

package org.squbs.httpclient.env

import akka.actor._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer

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

  def resolve(svcName: String): Environment
}

class EnvironmentRegistryExtension(system: ExtendedActorSystem) extends Extension with LazyLogging {
  val environmentResolvers = ListBuffer[EnvironmentResolver]()

  def register(resolver: EnvironmentResolver) = {
    environmentResolvers.find(_.name == resolver.name) match {
      case None =>
        environmentResolvers.prepend(resolver)
      case Some(oldResolver) =>
        logger.warn("Env Resolver:" + oldResolver.name + " already registered, skipped!")
    }
  }

  def unregister(name: String) = {
    environmentResolvers.find(_.name == name) match {
      case None =>
        logger.warn("Env Resolver:" + name + " cannot be found, skipping unregister!")
      case Some(resolver) =>
        environmentResolvers.remove(environmentResolvers.indexOf(resolver))
    }
  }

  def resolve(svcName: String): Environment = {
    val resolvedEnv = environmentResolvers.foldLeft[Environment](Default){
      (env: Environment, resolver: EnvironmentResolver) =>
        env match {
          case Default => resolver.resolve(svcName)
          case _       => env
        }
    }
    logger.debug(s"Environment can be resolved by ($svcName), the environment is: " + resolvedEnv.lowercaseName)
    resolvedEnv
  }
}

object EnvironmentRegistry extends ExtensionId[EnvironmentRegistryExtension] with ExtensionIdProvider {

  override def lookup() = EnvironmentRegistry

  override def createExtension(system: ExtendedActorSystem): EnvironmentRegistryExtension =
    new EnvironmentRegistryExtension(system)

  override def get(system: ActorSystem): EnvironmentRegistryExtension = super.get(system)
}


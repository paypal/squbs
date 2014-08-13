/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient.env

import org.slf4j.LoggerFactory
import scala.collection.mutable.ListBuffer

abstract class Environment {
  def name: String

  def lowercaseName: String = name.toLowerCase
}

case object Default extends Environment{
  override val name: String = "DEFAULT"
}

case object QA extends Environment {
  override val name: String = "QA"
}

case object DEV extends Environment {
  override val name: String = "DEV"
}

case object PROD extends Environment {
  override val name: String = "PROD"
}

case class RawEnv(name: String) extends Environment

trait EnvironmentResolver {
  def name: String

  def resolve(svcName: String): Option[Environment]
}

object EnvironmentRegistry {
  val environmentResolvers = ListBuffer[EnvironmentResolver]()

  val logger = LoggerFactory.getLogger(EnvironmentRegistry.getClass)

  def register(resolver: EnvironmentResolver) = {
    environmentResolvers.find(_.name == resolver.name) match {
      case None =>
        environmentResolvers.prepend(resolver)
      case Some(resolver) =>
        logger.info("Env Resolver:" + resolver.name + " has been registry, skip current env resolver registry!")
    }
  }

  def unregister(name: String) = {
    environmentResolvers.find(_.name == name) match {
      case None =>
        logger.warn("Env Resolver:" + name + " cannot be found, skip current env resolver unregistry!")
      case Some(resolver) =>
        environmentResolvers.remove(environmentResolvers.indexOf(resolver))
    }
  }

  def resolve(svcName: String): Environment = {
    environmentResolvers.find(_.resolve(svcName) != None) match {
      case Some(resolver) =>
        resolver.resolve(svcName).getOrElse(Default)
      case None           =>
        Default
    }
  }
}



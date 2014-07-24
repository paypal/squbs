package org.squbs.httpclient.env

import org.slf4j.LoggerFactory
import scala.collection.mutable.ListBuffer

/**
 * Created by hakuang on 7/17/2014.
 */

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



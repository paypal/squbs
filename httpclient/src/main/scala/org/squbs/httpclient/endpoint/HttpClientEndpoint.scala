package org.squbs.httpclient.endpoint

import scala.collection.mutable.ListBuffer
import org.slf4j.LoggerFactory
import org.squbs.httpclient.config.Configuration

/**
 * Created by hakuang on 5/9/2014.
 */

case class Endpoint(uri: String, properties: Map[String, Any] = Map(Endpoint.defaultConfiguration))

object Endpoint {
  val defaultConfigurationKey   = "org.squbs.httpclient.config.Configuration"
  val defaultConfigurationValue = Configuration()
  val defaultConfiguration      = defaultConfigurationKey -> defaultConfigurationValue
}

trait EndpointResolver {
  
  def name: String

  def resolve(svcName: String, env: Option[String] = None): Option[Endpoint]
}

object EndpointRegistry {

  val endpointResolvers = ListBuffer[EndpointResolver]()

  val logger = LoggerFactory.getLogger(EndpointRegistry.getClass)

  def register(resolver: EndpointResolver) = {
    endpointResolvers.find(_.name == resolver.name) match {
      case None =>
        endpointResolvers.prepend(resolver)
      case Some(routing) =>
        logger.info("Resolver:" + resolver.name + " has been registry, skip current endpoint resolver registry!")
    }
  }

  def unregister(name: String) = {
    endpointResolvers.find(_.name == name) match {
      case None =>
        logger.warn("Resolver:" + name + " cannot be found, skip current endpoint resolver unregistry!")
      case Some(route) =>
        endpointResolvers.remove(endpointResolvers.indexOf(route))
    }                                      
  }

  def route(svcName: String, env: Option[String] = None): Option[EndpointResolver] = {
    endpointResolvers.find(_.resolve(svcName, env) != None)
  }

  def resolve(svcName: String, env: Option[String] = None): Option[Endpoint] = {
    endpointResolvers.find(_.resolve(svcName, env) != None) flatMap (_.resolve(svcName, env)) match {
      case Some(endpoint) =>
        Some(endpoint)
      case None if (svcName != null && (svcName.startsWith("http://") || svcName.startsWith("https://"))) =>
        Some(Endpoint(svcName))
      case _ =>
        None
    }
  }

  def updateConfig(svcName: String, env: Option[String] = None, configuration: Configuration) = {
    route(svcName, env) match {
      case Some(existResolver) =>
        val position = endpointResolvers.indexOf(existResolver)
        endpointResolvers.update(position, new EndpointResolver {
          override def resolve(svcName: String, env: Option[String]): Option[Endpoint] = {
            val endpoint = existResolver.resolve(svcName,env).get
            Some(Endpoint(endpoint.uri, endpoint.properties.updated(Endpoint.defaultConfigurationKey, configuration)))
          }
          override def name: String = existResolver.name
        })
      case None =>
        logger.warn(s"There isn't any existing resolver which can resolve ($svcName, $env), ignore the update!")
    }
  }
}


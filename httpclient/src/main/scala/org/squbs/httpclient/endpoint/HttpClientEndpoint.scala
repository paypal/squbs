package org.squbs.httpclient.endpoint

import scala.collection.mutable.ListBuffer
import org.slf4j.LoggerFactory
import org.squbs.httpclient.env.{Default, Environment}
import org.squbs.httpclient.{Client, Configuration}

/**
 * Created by hakuang on 5/9/2014.
 */

case class Endpoint(uri: String, config: Configuration = Configuration())

object Endpoint {

  def check(endpoint: String) = {
    require(endpoint.toLowerCase.startsWith("http://") || endpoint.toLowerCase.startsWith("https://"), "service should be start with http:// or https://")
  }
}

trait EndpointResolver {
  
  def name: String

  def resolve(svcName: String, env: Environment = Default): Option[Endpoint]
}

object EndpointRegistry {

  val endpointResolvers = ListBuffer[EndpointResolver]()

  val logger = LoggerFactory.getLogger(EndpointRegistry.getClass)

  def register(resolver: EndpointResolver) = {
    endpointResolvers.find(_.name == resolver.name) match {
      case None =>
        endpointResolvers.prepend(resolver)
      case Some(routing) =>
        logger.info("Endpoint Resolver:" + resolver.name + " has been registry, skip current endpoint resolver registry!")
    }
  }

  def unregister(name: String) = {
    endpointResolvers.find(_.name == name) match {
      case None =>
        logger.warn("Endpoint Resolver:" + name + " cannot be found, skip current endpoint resolver unregistry!")
      case Some(resolver) =>
        endpointResolvers.remove(endpointResolvers.indexOf(resolver))
    }                                      
  }

  def route(svcName: String, env: Environment = Default): Option[EndpointResolver] = {
    endpointResolvers.find(_.resolve(svcName, env) != None)
  }

  def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    val resolvedEndpoint = endpointResolvers.foldLeft[Option[Endpoint]](None){(endpoint: Option[Endpoint], resolver: EndpointResolver) =>
      endpoint match {
        case Some(_) =>
          endpoint
        case None     =>
          resolver.resolve(svcName, env)
      }
    }
    resolvedEndpoint match {
      case Some(_) => resolvedEndpoint
      case None if (svcName != null && (svcName.startsWith("http://") || svcName.startsWith("https://"))) =>
        Some(Endpoint(svcName))
      case _ =>
        None
    }
  }

  def updateConfig(client: Client, configuration: Configuration) = {
    val serviceName = client.name
    val serviceEnv = client.env
    client.endpoint match {
      case Some(endpoint) =>
        val newEndpoint = Some(Endpoint(endpoint.uri, configuration))
        client.endpoint = newEndpoint
      case None =>
        logger.warn(s"There isn't any existing endpoint resolver which can resolve ($serviceName, $serviceEnv), ignore the update!")
    }
  }
}


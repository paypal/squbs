package org.squbs.httpclient.endpoint

import scala.collection.mutable.ListBuffer

/**
 * Created by hakuang on 5/9/2014.
 */
trait EndpointResolver {
  
  def name: String

  def resolve(svcName: String, env: Option[String] = None): Option[String]
}

object EndpointRegistry {

  val endpointResolvers = ListBuffer[EndpointResolver]()

  def register(routingDefinition: EndpointResolver) = {
    endpointResolvers.find(_.name == routingDefinition.name) match {
      case None => endpointResolvers.prepend(routingDefinition)
      case Some(routing) => println("routing:" + routingDefinition.name + " has been registry, skip current routing registry!")
    }
  }

  def unregister(name: String) = {
    endpointResolvers.find(_.name == name) match {
      case None => println("routing:" + name + " cannot be found, skip current routing unregistry!")
      case Some(route) => endpointResolvers.remove(endpointResolvers.indexOf(route))
    }                                      
  }

  def route(svcName: String, env: Option[String] = None): Option[EndpointResolver] = {
    endpointResolvers.find(_.resolve(svcName, env) != None)
  }

  def resolve(svcName: String, env: Option[String] = None): Option[String] = {
    endpointResolvers.find(_.resolve(svcName, env) != None) flatMap (_.resolve(svcName, env)) match {
      case Some(endpoint) => Some(endpoint)
      case None if (svcName != null && (svcName.startsWith("http://") || svcName.startsWith("https://"))) => Some(svcName)
      case _ => None
    }
  }
}


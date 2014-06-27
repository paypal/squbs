package org.squbs.hc.routing

import scala.collection.mutable.ListBuffer

/**
 * Created by hakuang on 5/9/2014.
 */
trait RoutingDefinition {
  
  def name: String

  def resolve(svcName: String, env: Option[String] = None): Option[String]
}

object RoutingRegistry {

  val routingDefinitions = ListBuffer[RoutingDefinition]()

  def register(routingDefinition: RoutingDefinition) = {
    routingDefinitions.find(_.name == routingDefinition.name) match {
      case None => routingDefinitions.prepend(routingDefinition)
      case Some(routing) => println("routing:" + routingDefinition.name + " has been registry, skip current routing registry!")
    }
  }

  def unregister(name: String) = {
    routingDefinitions.find(_.name == name) match {
      case None => println("routing:" + name + " cannot be found, skip current routing unregistry!")
      case Some(route) => routingDefinitions.remove(routingDefinitions.indexOf(route))
    }                                      
  }

  def route(svcName: String, env: Option[String] = None): Option[RoutingDefinition] = {
    routingDefinitions.find(_.resolve(svcName, env) != None)
  }

  def resolve(svcName: String, env: Option[String] = None): Option[String] = {
    routingDefinitions.find(_.resolve(svcName, env) != None) flatMap (_.resolve(svcName, env))
  }
}


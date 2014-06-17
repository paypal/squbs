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

  private val _routing = ListBuffer[RoutingDefinition]()

  def register(routingDefinition: RoutingDefinition) = {
    _routing.find(_.name == routingDefinition.name) match {
      case None => _routing.prepend(routingDefinition)
      case Some(routing) => println("routing:" + routingDefinition.name + " has been registry, skip current routing registry!")
    }
  }

  def unregister(name: String) = {
    _routing.find(_.name == name) match {
      case None => println("routing:" + name + " cannot be found, skip current routing unregistry!")
      case Some(routing) => _routing.remove(_routing.indexOf(routing))
    }                                      
  }

  def clear = {
    _routing.clear()
  }

  def get = _routing

  def routing(svcName: String, env: Option[String] = None): Option[RoutingDefinition] = {
    _routing.find(_.resolve(svcName, env) != None)
  }

  def resolve(svcName: String, env: Option[String] = None): Option[String] = {
    _routing.find(_.resolve(svcName, env) != None) flatMap (_.resolve(svcName, env))
  }
}

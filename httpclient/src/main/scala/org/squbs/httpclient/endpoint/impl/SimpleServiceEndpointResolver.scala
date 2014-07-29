package org.squbs.httpclient.endpoint.impl

import org.squbs.httpclient.env.Environment
import org.squbs.httpclient.Configuration
import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver}

/**
 * Created by hakuang on 7/22/2014.
 */
case class SimpleServiceEndpointResolver(resolverName: String, serviceMap: Map[String, Configuration]) extends EndpointResolver{

  serviceMap.foreach(service => Endpoint.check(service._1))

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    serviceMap.contains(svcName) match {
      case true =>
        val config = serviceMap.get(svcName).getOrElse(Configuration())
        Some(Endpoint(svcName, config))
      case false =>
        None
    }
  }

  override def name: String = resolverName
}

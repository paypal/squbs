package org.squbs.httpclient.dummy

import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver}
import org.squbs.httpclient.env.{DEV, Default, Environment}
import org.squbs.httpclient.HttpClientException

/**
 * Created by hakuang on 7/22/2014.
 */
object DummyServiceEndpointResolver extends EndpointResolver{

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    svcName match {
      case name => Some(Endpoint("http://localhost:9999"))
      case _    => None
    }
  }

  override def name: String = "DummyService"
}

object NotExistingEndpointResolver extends EndpointResolver {

  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    svcName match {
      case name => Some(Endpoint("http://www.notexistingservice.com"))
      case _    => None
    }
  }

  override def name: String = "NotExistingService"
}

object DummyLocalhostResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    if (svcName == null && svcName.length <= 0) throw new HttpClientException(700, "Service name cannot be null")
    env match {
      case Default | DEV => Some(Endpoint("http://localhost:8080/" + svcName))
      case _   => throw new HttpClientException(701, "DummyLocalhostResolver cannot support " + env + " environment")
    }
  }

  override def name: String = "DummyLocalhostResolver"
}

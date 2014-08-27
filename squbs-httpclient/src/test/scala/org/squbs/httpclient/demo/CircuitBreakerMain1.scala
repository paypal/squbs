package org.squbs.httpclient.demo

import org.squbs.httpclient.{CircuitBreakerConfiguration, Configuration, HttpClientFactory}
import scala.util.{Failure, Success}
import akka.pattern.CircuitBreakerOpenException
import scala.concurrent.duration._
import akka.actor.ActorSystem
import org.squbs.httpclient.endpoint.{Endpoint, EndpointResolver, EndpointRegistry}
import org.squbs.httpclient.env.Environment

/**
 * Created by hakuang on 8/15/2014.
 */
object CircuitBreakerMain1 extends App{

  implicit val actorSystem = ActorSystem("CircuitBreakerMain1")
//  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val ec = actorSystem.dispatcher

  EndpointRegistry.register(new EndpointResolver{

    override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
      svcName match {
        case name => Some(Endpoint("http://localhost:8888"))
        case _    => None
      }
    }

    override def name: String = "DummyService"
  })
  val httpClient = HttpClientFactory.get("DummyService").withConfig(Configuration().copy(hostSettings = Configuration.defaultHostSettings.copy(maxRetries = 0), circuitBreakerConfig = CircuitBreakerConfiguration().copy(callTimeout = 1 second)))
  while(true){
    Thread.sleep(2000)
    httpClient.get("/view") onComplete {
      case Success(httpResponse) =>
        println("call success, body is:" + httpResponse.entity.data.asString + ",status:" + httpClient.cbStatus)
      case Failure(e: CircuitBreakerOpenException) =>
        println("circuitBreaker open! remaining time is:" + e.remainingDuration.toSeconds + ", status:" + httpClient.cbStatus)
      case Failure(throwable) =>
        println("exception is:" + throwable.getMessage + ", status:" + httpClient.cbStatus)
    }
  }
}

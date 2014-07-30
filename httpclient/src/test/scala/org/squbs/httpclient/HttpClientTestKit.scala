package org.squbs.httpclient

import org.squbs.httpclient.endpoint.EndpointRegistry
import org.squbs.httpclient.env.EnvironmentRegistry
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import akka.pattern._
import scala.concurrent.duration._
import spray.util._

/**
 * Created by hakuang on 7/29/2014.
 */
trait HttpClientTestKit {

  def clearHttpClient = {
    EndpointRegistry.endpointResolvers.clear
    EnvironmentRegistry.environmentResolvers.clear
    HttpClientFactory.httpClientMap.clear
  }
  
  def shutdownActorSystem(implicit system: ActorSystem) = {
    IO(Http).ask(Http.CloseAll)(30.second).await
    system.shutdown()  
  }
}

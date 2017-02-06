/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.httpclient

import java.lang.management.ManagementFactory
import javax.management.ObjectName

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import org.squbs.env.QA
import org.squbs.resolver._
import org.squbs.unicomplex.JMX
import org.squbs.util.ConfigUtil._

import scala.concurrent.duration.Duration

object ClientConfigurationSpec {

  val defaultConfig = ConfigFactory.load()

  val appConfig = ConfigFactory.parseString(
    s"""
       |squbs {
       |  ${JMX.prefixConfig} = true
       |}
       |
       |sampleClient {
       |  type = squbs.httpclient
       |
       |  akka.http {
       |    host-connection-pool {
       |      max-connections = 987
       |      max-retries = 123
       |
       |      client = {
       |        connecting-timeout = 123 ms
       |      }
       |    }
       |  }
       |}
       |
       |sampleClient2 {
       |  type = squbs.httpclient
       |
       |  akka.http.host-connection-pool {
       |    max-connections = 666
       |  }
       |}
       |
       |sampleClientWithEnv {
       |  type = squbs.httpclient
       |
       |  akka.http.host-connection-pool {
       |    max-connections = 100
       |  }
       |
       |  QA {
       |    akka.http.host-connection-pool {
       |      max-connections = 5
       |    }
       |  }
       |}
       |
       |noOverrides {
       |  type = squbs.httpclient
       |}
       |
       |noType {
       |
       |  akka.http.host-connection-pool {
       |    max-connections = 987
       |    max-retries = 123
       |  }
       |}
       |
       |passedAsParameter {
       |  type = squbs.httpclient
       |
       |  akka.http.host-connection-pool {
       |    max-connections = 111
       |  }
       |}
    """.stripMargin)


  implicit val system = ActorSystem("ClientConfigurationSpec", appConfig.withFallback(defaultConfig))
  implicit val materializer = ActorMaterializer()

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver")
    { (_, _) => Some(HttpEndpoint(s"http://localhost:1234")) }


}

class ClientConfigurationSpec extends FlatSpec with Matchers {

  import ClientConfigurationSpec._

  it should "give priority to client specific configuration" in {
    ClientFlow("sampleClient")
    assertJmxValue("sampleClient", "MaxConnections",
      appConfig.getInt("sampleClient.akka.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient", "MaxRetries",
      appConfig.getInt("sampleClient.akka.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("akka.http.host-connection-pool.idle-timeout").toString)
    assertJmxValue("sampleClient", "ConnectingTimeout",
      appConfig.get[Duration]("sampleClient.akka.http.host-connection-pool.client.connecting-timeout").toString)
  }

  it should "fallback to default values if no client specific configuration is provided" in {
    ClientFlow("noSpecificConfiguration")
    assertDefaults("noSpecificConfiguration")
  }

  it should "fallback to default values if client configuration does not override any properties" in {
    ClientFlow("noOverrides")
    assertDefaults("noOverrides")
  }

  it should "ignore client specific configuration if type is not set to squbs.httpclient" in {
    ClientFlow("noType")
    assertDefaults("noType")
  }

  it should "let configuring multiple clients" in {
    ClientFlow("sampleClient2")

    assertJmxValue("sampleClient", "MaxConnections",
      appConfig.getInt("sampleClient.akka.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient", "MaxRetries",
      appConfig.getInt("sampleClient.akka.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("akka.http.host-connection-pool.idle-timeout").toString)
    assertJmxValue("sampleClient", "ConnectingTimeout",
      appConfig.get[Duration]("sampleClient.akka.http.host-connection-pool.client.connecting-timeout").toString)

    assertJmxValue("sampleClient2", "MaxConnections",
      appConfig.getInt("sampleClient2.akka.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient2", "MaxRetries", defaultConfig.getInt("akka.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient2", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("akka.http.host-connection-pool.idle-timeout").toString)
  }

  it should "honor environment-specific overrides" in {
    ClientFlow("sampleClientWithEnv", env = QA)

    assertJmxValue("sampleClientWithEnv", "MaxConnections",
      appConfig.getInt("sampleClientWithEnv.QA.akka.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClientWithEnv", "MaxRetries", defaultConfig.getInt("akka.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClientWithEnv", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("akka.http.host-connection-pool.idle-timeout").toString)
  }

  it should "configure even if not present in conf file" in {
    ClientFlow("notInConfig")
    assertDefaults("notInConfig")
  }

  it should "give priority to passed in settings" in {
    val MaxConnections = 8778
    val cps = ConnectionPoolSettings(system.settings.config).withMaxConnections(MaxConnections)
    ClientFlow("passedAsParameter", settings = Some(cps))
    assertJmxValue("passedAsParameter", "MaxConnections", MaxConnections)
  }

  def assertJmxValue(clientName: String, key: String, expectedValue: Any) = {
    val oName = ObjectName.getInstance(s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=$clientName")
    val actualValue = ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key)
    actualValue shouldEqual expectedValue
  }

  private def assertDefaults(clientName: String) = {
    assertJmxValue(clientName, "MaxConnections", defaultConfig.getInt("akka.http.host-connection-pool.max-connections"))
    assertJmxValue(clientName, "MaxRetries", defaultConfig.getInt("akka.http.host-connection-pool.max-retries"))
    assertJmxValue(clientName, "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("akka.http.host-connection-pool.idle-timeout").toString)
  }
}

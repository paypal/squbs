/*
 * Copyright 2017 PayPal
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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.resolver._
import org.squbs.unicomplex.JMX
import org.squbs.util.ConfigUtil._

import java.lang.management.ManagementFactory
import javax.management.ObjectName
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
       | type = squbs.httpclient
       |
       | pekko.http {
       |   host-connection-pool {
       |     max-connections = 987
       |     max-retries = 123
       |
       |     client = {
       |       connecting-timeout = 123 ms
       |     }
       |   }
       | }
       |}
       |
       |sampleClient2 {
       | type = squbs.httpclient
       |
       | pekko.http.host-connection-pool {
       |   max-connections = 666
       | }
       |}
       |
       |noOverrides {
       | type = squbs.httpclient
       |}
       |
       |noType {
       |
       | pekko.http.host-connection-pool {
       |   max-connections = 987
       |   max-retries = 123
       | }
       |}
       |
       |passedAsParameter {
       | type = squbs.httpclient
       |
       | pekko.http.host-connection-pool {
       |   max-connections = 111
       | }
       |}
       |
       |resolverConfig {
       |  type = squbs.httpclient
       |  pekko.http.host-connection-pool {
       |    max-connections = 111
       |  }
       |}
    """.stripMargin)

  val resolverConfig = ConfigFactory.parseString(
    """
      |pekko.http.host-connection-pool {
      |  max-connections = 987
      |  max-retries = 123
      |}
    """.stripMargin)

  implicit val system = ActorSystem("ClientConfigurationSpec", appConfig.withFallback(defaultConfig))

  ResolverRegistry(system).register[HttpEndpoint]("LocalhostEndpointResolver") { (name, _) =>
    name match {
      case "resolverConfig" => Some(HttpEndpoint(s"http://localhost:1234", None, Some(resolverConfig)))
      case _ => Some(HttpEndpoint(s"http://localhost:1234"))
    }
  }

  trait TypeConverter[T] {
    def convert(src: Any): T
  }

  implicit val durationConverter = new TypeConverter[Duration] {
    override def convert(src: Any): Duration = src match {
      case d: Duration => d
      case x => Duration(x.toString)
    }
  }
  implicit val stringConverter = new TypeConverter[String] {
    override def convert(src: Any): String = src.toString
  }
  implicit val intConverter = new TypeConverter[Int] {
    override def convert(src: Any):Int = src match {
      case i: Int => i
      case x => x.toString.toInt
    }
  }
}

class ClientConfigurationSpec extends AnyFlatSpec with Matchers {

  import ClientConfigurationSpec._

  it should "give priority to client specific configuration" in {
    ClientFlow("sampleClient")
    assertJmxValue("sampleClient", "MaxConnections",
      appConfig.getInt("sampleClient.pekko.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient", "MaxRetries",
      appConfig.getInt("sampleClient.pekko.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("pekko.http.host-connection-pool.idle-timeout"))
    assertJmxValue("sampleClient", "ConnectingTimeout",
      appConfig.get[Duration]("sampleClient.pekko.http.host-connection-pool.client.connecting-timeout"))
  }

  it should "fallback to default values if no client specific configuration is provided" in {
    ClientFlow("noSpecificConfiguration")
    assertDefaults("noSpecificConfiguration")
  }

  it should "fallback to default values if client configuration does not override any properties" in {
    ClientFlow("noOverrides")
    assertDefaults("noOverrides")
  }

  it should "fallback to resolver config first then default values if client configuration is missing the property" in {
    ClientFlow("resolverConfig")
    assertJmxValue("resolverConfig", "MaxConnections", 111)
    assertJmxValue("resolverConfig", "MaxRetries", 123)
    assertJmxValue("resolverConfig", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("pekko.http.host-connection-pool.idle-timeout"))
  }

  it should "ignore client specific configuration if type is not set to squbs.httpclient" in {
    ClientFlow("noType")
    assertDefaults("noType")
  }

  it should "let configuring multiple clients" in {
    ClientFlow("sampleClient2")

    assertJmxValue("sampleClient", "MaxConnections",
      appConfig.getInt("sampleClient.pekko.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient", "MaxRetries",
      appConfig.getInt("sampleClient.pekko.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("pekko.http.host-connection-pool.idle-timeout"))
    assertJmxValue("sampleClient", "ConnectingTimeout",
      appConfig.get[Duration]("sampleClient.pekko.http.host-connection-pool.client.connecting-timeout"))

    assertJmxValue("sampleClient2", "MaxConnections",
      appConfig.getInt("sampleClient2.pekko.http.host-connection-pool.max-connections"))
    assertJmxValue("sampleClient2", "MaxRetries", defaultConfig.getInt("pekko.http.host-connection-pool.max-retries"))
    assertJmxValue("sampleClient2", "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("pekko.http.host-connection-pool.idle-timeout"))
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

  private def assertJmxValue[T: TypeConverter](clientName: String, key: String, expectedValue: T) = {
    val oName = ObjectName.getInstance(
      s"org.squbs.configuration.${system.name}:type=squbs.httpclient,name=${ObjectName.quote(clientName)}")
    val actualValue = implicitly[TypeConverter[T]]
      .convert(ManagementFactory.getPlatformMBeanServer.getAttribute(oName, key))
    actualValue shouldEqual expectedValue
  }

  private def assertDefaults(clientName: String) = {
    assertJmxValue(clientName, "MaxConnections", defaultConfig.getInt("pekko.http.host-connection-pool.max-connections"))
    assertJmxValue(clientName, "MaxRetries", defaultConfig.getInt("pekko.http.host-connection-pool.max-retries"))
    assertJmxValue(clientName, "ConnectionPoolIdleTimeout",
      defaultConfig.get[Duration]("pekko.http.host-connection-pool.idle-timeout"))
  }
}

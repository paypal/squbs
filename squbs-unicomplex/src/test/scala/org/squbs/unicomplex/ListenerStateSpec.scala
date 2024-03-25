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

package org.squbs.unicomplex

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.JMX._

import javax.management.ObjectName
import javax.management.openmbean.CompositeData

object ListenerStateSpec{

  val classPaths = Array(getClass.getClassLoader.getResource("classpaths/ListenerState").getPath)

  val (_, _, port) = temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |default-listener.bind-port = $port
       |squbs {
       |  actorsystem-name = listenerStateSpec
       |  ${JMX.prefixConfig} = true
       |}
       |
       |port-conflict-listener {
       |  type = squbs.listener
       |  aliases = []
       |
       |  # Service bind to particular address/interface. The default is 0.0.0.0 which is any address/interface.
       |  bind-address = "0.0.0.0"
       |
       |  # Whether or not using full host name for address binding
       |  full-address = false
       |
       |  # Service bind to particular port. 8080 is the default.
       |  bind-port = $port
       |
       |  # Listener uses HTTPS?
       |  secure = false
       |
       |  # HTTPS needs client authorization? This configuration is not read if secure is false.
       |  need-client-auth = false
       |
       |  # Any custom SSLContext provider? Setting to "default" means platform default.
       |  ssl-context = default
       |}
       |
       |ssl-context-not-exist-listener {
       |  type = squbs.listener
       |  aliases = []
       |
       |  # Service bind to particular address/interface. The default is 0.0.0.0 which is any address/interface.
       |  bind-address = "0.0.0.0"
       |
       |  # Whether or not using full host name for address binding
       |  full-address = false
       |
       |  # Service bind to particular port. 8080 is the default.
       |  bind-port = 0
       |
       |  # Listener uses HTTPS?
       |  secure = true
       |
       |  # HTTPS needs client authorization? This configuration is not read if secure is false.
       |  need-client-auth = false
       |
       |  # Any custom SSLContext provider? Setting to "default" means platform default.
       |  ssl-context = org.squbs.unicomplex.IDoNotExist
       |}
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()
}

class ListenerStateSpec extends TestKit(ListenerStateSpec.boot.actorSystem) with AnyFlatSpecLike
  with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "ListenerState JMX bean" should "report startup errors" in {
    val listenerStates = JMX.get(new ObjectName(prefix(system) + listenerStateName), "ListenerStates")
      .asInstanceOf[Array[CompositeData]]

    listenerStates should have length 3

    val defaultListener = listenerStates.find(_.get("listener") == "default-listener").get
    val portConflictListener = listenerStates.find(_.get("listener") == "port-conflict-listener").get
    val sslContextNotExistListener = listenerStates.find(_.get("listener") == "ssl-context-not-exist-listener").get

    // Due to timing issues, either the default listener or the portConflictListener will succeed, but not both.
    val defaultListenerSuccess = defaultListener.get("state") == "Success"
    val portConflictListenerSuccess = portConflictListener.get("state") == "Success"
    defaultListenerSuccess ^ portConflictListenerSuccess shouldBe true
    val failingListener = if (defaultListenerSuccess) portConflictListener else defaultListener
    val errMsg = failingListener.get("error").asInstanceOf[String]
    errMsg should include ("Bind failed because of java.net.BindException:")
    errMsg should include ("Address already in use")
    sslContextNotExistListener.get("state") should be("Failed")
    sslContextNotExistListener.get("error").asInstanceOf[String] should startWith(
      "java.lang.ClassNotFoundException: org.squbs.unicomplex.IDoNotExist")
  }
}

class ListenerStateService extends RouteDefinition with Directives {

  override def route: Route = get {
    complete(StatusCodes.OK)
  }
}

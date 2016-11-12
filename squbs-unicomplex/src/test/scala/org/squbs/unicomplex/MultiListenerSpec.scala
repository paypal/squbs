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

package org.squbs.unicomplex

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await

object MultiListenerSpecActorSystem {
  
  val config = ConfigFactory.parseString(
      s"""
        squbs {
          actorsystem-name = MultiListen
          ${JMX.prefixConfig} = true
        }
        default-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = 0
          secure = false
          need-client-auth = false
          ssl-context = default
        }
        second-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = 0
          secure = false
          need-client-auth = false
          ssl-context = default
        }
        third-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.111.0.0" # Invalid address - bind should fail
          full-address = false
          bind-port = 25 # Restricted port - bind should fail
          secure = false
          need-client-auth = false
          ssl-context = default
        }
      """.stripMargin)

  val jarDir = getClass.getClassLoader.getResource("classpaths").getPath

  val boot = UnicomplexBoot(config)
    		.createUsing { (name, config) => ActorSystem(name, config)}
  			.scanComponents(Seq(new File(jarDir + "/MultiListeners").getAbsolutePath))
  			.initExtensions
  			.start()
}

class MultiListenerSpec extends TestKit(MultiListenerSpecActorSystem.boot.actorSystem)
    with FlatSpecLike with BeforeAndAfterAll with Matchers {

  implicit val am = ActorMaterializer()

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port1 = portBindings("default-listener")
  val port2 = portBindings("second-listener")

  behavior of "The unicomplex"

  it should "not have 'third-listener' in the portBindings" in {
    portBindings should have size 2
    portBindings should not contain key ("third-listener")
  }

  it should "run up two listeners on different ports" in {
    Await.result(get(s"http://127.0.0.1:$port1/multi"), awaitMax).status should be (StatusCodes.OK)
    Await.result(get(s"http://127.0.0.1:$port2/multi"), awaitMax).status should be (StatusCodes.OK)
  }

  it should "only have started the application once" in {
    MultiListenerService.count should be(1)
  }

  it should "register the JMXBean for Akka Http status" in {
    import org.squbs.unicomplex.JMX._
    val statsBase = prefix(system) + serverStats
    get(statsBase + "default-listener", "ListenerName").asInstanceOf[String] should be ("default-listener")
    get(statsBase + "default-listener", "TotalConnections").asInstanceOf[Long] should be >= 0L
//    get(statsBase + "default-listener", "RequestsTimedOut").asInstanceOf[Long] should be >= 0L // TODO Missing feature
    get(statsBase + "default-listener", "OpenRequests").asInstanceOf[Long] should be >= 0L
    get(statsBase + "default-listener", "Uptime").asInstanceOf[String] should fullyMatch regex """\d{2}:\d{2}:\d{2}\.\d{3}"""
    get(statsBase + "default-listener", "MaxOpenRequests").asInstanceOf[Long] should be >= 0L
    get(statsBase + "default-listener", "OpenConnections").asInstanceOf[Long] should be >= 0L
    get(statsBase + "default-listener", "MaxOpenConnections").asInstanceOf[Long] should be >= 0L
    get(statsBase + "default-listener", "TotalRequests").asInstanceOf[Long] should be >= 0L


    get(statsBase + "second-listener", "ListenerName").asInstanceOf[String] should be ("second-listener")
    get(statsBase + "second-listener", "TotalConnections").asInstanceOf[Long] should be >= 0L
//    get(statsBase + "second-listener", "RequestsTimedOut").asInstanceOf[Long] should be >= 0L // TODO Missing feature
    get(statsBase + "second-listener", "OpenRequests").asInstanceOf[Long] should be >= 0L
    get(statsBase + "second-listener", "Uptime").asInstanceOf[String] should fullyMatch regex """\d{2}:\d{2}:\d{2}\.\d{3}"""
    get(statsBase + "second-listener", "MaxOpenRequests").asInstanceOf[Long] should be >= 0L
    get(statsBase + "second-listener", "OpenConnections").asInstanceOf[Long] should be >= 0L
    get(statsBase + "second-listener", "MaxOpenConnections").asInstanceOf[Long] should be >= 0L
    get(statsBase + "second-listener", "TotalRequests").asInstanceOf[Long] should  be >= 0L

  }

  override protected def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }
}

class MultiListenerService extends RouteDefinition {
  MultiListenerService.inc()


  override def route: Route = get {
    complete(StatusCodes.OK)
  }
}

object MultiListenerService {
  private val counter = new AtomicInteger(0)

  def count = counter.get

  def inc() = counter.incrementAndGet()
}
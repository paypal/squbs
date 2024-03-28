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
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.pattern._
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await

object FlowDefinitionSpec {

  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath
  val classPath = dummyJarsDir + "/FlowDefinitionSpec/META-INF/squbs-meta.conf"

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = FlowDefinitionSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = 0
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanResources(withClassPath = false, classPath)
    .initExtensions.start()

}

class TestFlowDefinition extends FlowDefinition with WebContext {
  val firstPath = s"/$webContext/first"
  val secondPath = s"/$webContext/second"
  val thirdPath = s"/$webContext/third"

  @volatile var count = 0
  def flow = Flow[HttpRequest].map {
    case HttpRequest(HttpMethods.GET, Uri(_, _, Path(`firstPath`), _, _), _, _, _) =>
      count += 1
      HttpResponse(StatusCodes.OK, entity = count.toString)

    case HttpRequest(HttpMethods.GET, Uri(_, _, Path(`secondPath`), _, _), _, _, _) =>
      count += 1
      HttpResponse(StatusCodes.OK, entity = count.toString)

    case HttpRequest(HttpMethods.GET, Uri(_, _, Path(`thirdPath`), _, _), _, _, _) =>
      HttpResponse(StatusCodes.OK, entity = {count += 1; count.toString})
  }
}

class FlowDefinitionSpec extends TestKit(
  FlowDefinitionSpec.boot.actorSystem) with AnyFlatSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port = portBindings("default-listener")

  override def afterAll(): Unit = {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "The test actor" should "return correct count value" in {
    // The behaviour is different than Spray.  Not caching anymore.
    Await.result(entityAsString(s"http://127.0.0.1:$port/flowdef/first"), awaitMax) should be ("1")
    Await.result(entityAsString(s"http://127.0.0.1:$port/flowdef/first"), awaitMax) should be ("2")
    Await.result(entityAsString(s"http://127.0.0.1:$port/flowdef/second"), awaitMax) should be ("3")
    Await.result(entityAsString(s"http://127.0.0.1:$port/flowdef/third"), awaitMax) should be ("4")
    Await.result(entityAsString(s"http://127.0.0.1:$port/flowdef/third"), awaitMax) should be ("5")
  }
}

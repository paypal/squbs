/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.squbs.stream

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, MergeHub, Sink}
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.unicomplex.Timeouts.{awaitMax, _}
import org.squbs.unicomplex._

import scala.collection.mutable
import scala.concurrent.Await

object PerpetualStreamMergeHubSpec {
  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath
  val classPaths = Array("PerpetualStreamMergeHubSpec") map (dummyJarsDir + "/" + _)

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = PerpetualStreamMergeHubSpec
       |  ${JMX.prefixConfig} = true
       |}
      """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {
      (name, config) => ActorSystem(name, config)
    }
    .scanComponents(classPaths)
    .start()
}

class PerpetualStreamMergeHubSpec extends TestKit(PerpetualStreamMergeHubSpec.boot.actorSystem)
  with FlatSpecLike with Matchers  {

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val psActorName = "/user/PerpetualStreamMergeHubSpec/perpetualStreamWithMergeHub"
  val actorRef = Await.result((system.actorSelection(psActorName) ? RetrieveMyMessageStorageActorRef).mapTo[ActorRef], awaitMax)
  val port = portBindings("default-listener")


  it should "connect streams with mergehub" in {

    implicit val ac = ActorMaterializer()
    Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$port/mergehub"), entity = "10"))
    Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$port/mergehub"), entity = "11"))

    awaitAssert {
      val messages = Await.result((actorRef ? RetrieveMyMessages).mapTo[mutable.Set[MyMessage]], awaitMax)
      messages should have size 2
      messages should contain(MyMessage(10))
      messages should contain(MyMessage(11))
    }
  }
}

case class MyMessage(id: Int)

class HttpFlowWithMergeHub extends FlowDefinition with PerpetualStreamMatValue[Sink[MyMessage, NotUsed]] {

  import context.dispatcher

  import scala.concurrent.duration._
  implicit val mat = ActorMaterializer()

  implicit val myMessageUnmarshaller: FromEntityUnmarshaller[MyMessage] =
    Unmarshaller { implicit ex ⇒ entity ⇒ entity.toStrict(1.second).map(e => MyMessage(e.data.utf8String.toInt)) }

  override val flow: Flow[HttpRequest, HttpResponse, NotUsed] =
    Flow[HttpRequest]
      .mapAsync(1)(Unmarshal(_).to[MyMessage])
      .alsoTo(matValue("/user/PerpetualStreamMergeHubSpec/perpetualStreamWithMergeHub"))
      .map { myMessage => HttpResponse(entity = s"Received Id: ${myMessage.id}") }
}

class PerpetualStreamWithMergeHub extends PerpetualStream[Sink[MyMessage, NotUsed]] {

  override lazy val streamRunLifecycleState: LifecycleState = Initializing

  val source = MergeHub.source[MyMessage]

  val myMessageStorageActor = context.actorOf(Props[MyMessageStorageActor])

  /**
    * Describe your graph by implementing streamGraph
    *
    * @return The graph.
    */
  override def streamGraph= source.to(Sink.actorRef(myMessageStorageActor, "Done"))

  override def receive: Receive = {
    case RetrieveMyMessageStorageActorRef => sender() ! myMessageStorageActor
  }
}

object RetrieveMyMessages
object RetrieveMyMessageStorageActorRef

class MyMessageStorageActor extends Actor {

  val myMessageSet = mutable.Set[MyMessage]()

  override def receive: Receive = {
    case myMessage: MyMessage => myMessageSet.add(myMessage)
    case RetrieveMyMessages => sender() ! myMessageSet
  }
}
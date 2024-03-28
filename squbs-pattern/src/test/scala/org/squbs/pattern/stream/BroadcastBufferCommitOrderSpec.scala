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

package org.squbs.pattern.stream

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.scaladsl.{GraphDSL, RunnableGraph}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.testkit.Timeouts._

import scala.concurrent.Await

class BroadcastBufferCommitOrderSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll with Eventually {

  implicit val system = ActorSystem("BroadcastBufferCommitOrderSpec", PersistentBufferSpec.testConfig)
  implicit val serializer = QueueSerializer[Int]()
  import StreamSpecUtil._

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), awaitMax)
  }

  it should "fail when an out of order commit is attempted and commit-order-policy = strict" in {
    val util = new StreamSpecUtil[Int, Event[Int]](2)
    import util._
    val buffer = BroadcastBufferAtLeastOnce[Int](ConfigFactory.parseString("commit-order-policy = strict").withFallback(config))
    val streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(flowCounter) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val commit = buffer.commit[Int]
        val bcBuffer = builder.add(buffer.async)
        val mr = builder.add(merge)
        in ~> bcBuffer ~> filterARandomElement ~> commit ~> mr ~> sink
        bcBuffer ~> commit ~> mr
        ClosedShape
    })
    val sinkF = streamGraph.run()
    Await.result(sinkF.failed, awaitMax) shouldBe an[CommitOrderException]
    clean()
  }

  it should "not fail when an out of order commit is attempted and commit-order-policy = lenient" in {
    val util = new StreamSpecUtil[Int, Event[Int]](2)
    import util._
    val buffer = BroadcastBufferAtLeastOnce[Int](ConfigFactory.parseString("commit-order-policy = lenient").withFallback(config))
    val streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(flowCounter) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val commit = buffer.commit[Int]
      val bcBuffer = builder.add(buffer.async)
        val mr = builder.add(merge)
        in ~> bcBuffer ~> filterARandomElement ~> commit ~> mr ~> sink
        bcBuffer ~> commit ~> mr
        ClosedShape
    })

    val countFuture = streamGraph.run()
    val count = Await.result(countFuture, awaitMax)
    eventually { buffer.queue shouldBe 'closed }
    count shouldBe (elementCount * outputPorts - 1)
    println(s"Total records processed $count")

    clean()
  }
}

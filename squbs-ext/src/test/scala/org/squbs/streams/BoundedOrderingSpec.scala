/*
 * Copyright 2018 PayPal
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
package org.squbs.streams

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BoundedOrderingSpec extends AnyFlatSpec with Matchers with ScalaFutures {

  implicit val system = ActorSystem("OrderingStateSpec")

  it should "require waitFor > 0" in {
    an [IllegalArgumentException] should be thrownBy BoundedOrdering[Int, Int](maxBounded = 0, 1, _ + 1, identity)
  }

  it should "retain order of a stream" in {
    val boundedOrdering = BoundedOrdering[Int, Int](maxBounded = 5, 1, _ + 1, identity)
    val input = List(1, 2, 3, 4, 5)
    val output = Source(input).via(boundedOrdering).toMat(Sink.seq)(Keep.right).run()
    output.futureValue should contain theSameElementsInOrderAs input
  }

  it should "re-order the stream completely within the ordering range" in {
    val boundedOrdering = BoundedOrdering[Int, Int](maxBounded = 5, 1, _ + 1, identity)
    val input = List(2, 3, 4, 1, 5, 7, 8, 6, 9, 10)
    val output = Source(input).via(boundedOrdering).toMat(Sink.seq)(Keep.right).run()
    output.futureValue should contain theSameElementsInOrderAs input.sorted
  }

  it should "re-order the stream incompletely outside of the ordering range" in {
    val boundedOrdering = BoundedOrdering[Int, Int](maxBounded = 5, 1, _ + 1, identity)
    val input = List(1, 3, 4, 5, 6, 7, 8, 9, 2, 10)
    val output = Source(input).via(boundedOrdering).toMat(Sink.seq)(Keep.right).run()
    output.futureValue should contain theSameElementsInOrderAs input
  }

  it should "ignore the missing element and keep the stream moving" in {
    val boundedOrdering = BoundedOrdering[Int, Int](maxBounded = 5, 1, _ + 1, identity)
    val input = List(1, 3, 4, 5, 6, 7, 8, 9, 10, 11)
    val output = Source(input).via(boundedOrdering).toMat(Sink.seq)(Keep.right).run()
    output.futureValue should contain theSameElementsInOrderAs input
  }

  it should "re-order the stream with identifier type different from message type" in {
    case class Element(id: Long, content: String)
    val boundedOrdering = BoundedOrdering[Element, Long](maxBounded = 5, 1L, _ + 1L, _.id)
    val input = List(Element(1, "one"), Element(3, "three"), Element(5, "five"), Element(2, "two"), Element(6, "six"),
      Element(7, "seven"), Element(8, "eight"), Element(9, "nine"), Element(10, "ten"), Element(4, "four"))
    val wisb = List(Element(1, "one"), Element(2, "two"), Element(3, "three"), Element(5, "five"), Element(6, "six"),
      Element(7, "seven"), Element(8, "eight"), Element(9, "nine"), Element(10, "ten"), Element(4, "four"))

    val output = Source(input).via(boundedOrdering).toMat(Sink.seq)(Keep.right).run()
    output.futureValue should contain theSameElementsInOrderAs wisb
  }

  it should "re-order the stream using custom id ordering" in {
    case class Element(id: String, content: String)
    implicit val order: Ordering[String] = Ordering.by(_.toInt)
    val boundedOrdering = BoundedOrdering[Element, String](maxBounded = 5, "2", s => (s.toInt + 2).toString, _.id)
    val input = List(Element("2", "one"), Element("6", "three"), Element("10", "five"), Element("4", "two"),
      Element("12", "six"), Element("14", "seven"), Element("16", "eight"), Element("18", "nine"),
      Element("20", "ten"), Element("8", "four"))
    val wisb = List(Element("2", "one"), Element("4", "two"), Element("6", "three"), Element("10", "five"),
      Element("12", "six"), Element("14", "seven"), Element("16", "eight"), Element("18", "nine"),
      Element("20", "ten"), Element("8", "four"))

    val output = Source(input).via(boundedOrdering).toMat(Sink.seq)(Keep.right).run()
    output.futureValue should contain theSameElementsInOrderAs wisb
  }
}

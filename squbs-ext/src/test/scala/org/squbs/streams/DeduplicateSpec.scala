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

package org.squbs.streams

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util

class DeduplicateSpec extends AsyncFlatSpec with Matchers{

  implicit val system = ActorSystem("DeduplicateSpec")

  it should "require duplicateCount >= 2" in {
    an [IllegalArgumentException] should be thrownBy
      Source("a" :: "a" :: "b" :: "b" :: "c" :: "c" :: Nil).via(Deduplicate(1)).runWith(Sink.seq)

  }

  it should "filter consecutive duplicates" in {
    val result = Source("a" :: "a" :: "b" :: "b" :: "c" :: "c" :: Nil).via(Deduplicate(2)).runWith(Sink.seq)
    result map { s => s should contain theSameElementsInOrderAs ("a" :: "b" :: "c" :: Nil)}
  }

  it should "filter unordered duplicates" in {
    val result = Source("a" :: "b" :: "b" :: "c" :: "a" :: "c" :: Nil).via(Deduplicate(2)).runWith(Sink.seq)
    result map { s => s should contain theSameElementsInOrderAs ("a" :: "b" :: "c" :: Nil)}
  }

  it should "filter when identical element count is greater than 2" in {
    val result = Source("a" :: "a" :: "b" :: "b" :: "b" :: "c" :: "a" :: "c" :: "c" :: Nil).
      via(Deduplicate(3)).runWith(Sink.seq)
    result map { s => s should contain theSameElementsInOrderAs ("a" :: "b" :: "c" :: Nil)}
  }

  it should "filter all identical elements when duplicate count is not specified" in {
    val result = Source("a" :: "b" :: "b" :: "c" :: "a" :: "a" :: "a" :: "c" :: Nil).via(Deduplicate()).runWith(Sink.seq)
    result map { s => s should contain theSameElementsInOrderAs ("a" :: "b" :: "c" :: Nil)}
  }

  it should "allow identical elements after duplicate count is reached" in {
    val result = Source("a" :: "a" :: "b" :: "b" :: "b" :: "c" :: "a" :: "c" :: "c" :: Nil).
      via(Deduplicate(2)).runWith(Sink.seq)
    result map { s => s should contain theSameElementsInOrderAs ("a" :: "b" :: "b" :: "c" :: "a" :: "c" :: Nil)}
  }

  it should "remove an element from registry when duplicate count is reached" in {
    val map = new util.HashMap[String, MutableLong]()
    val result = Source("a" :: "a" :: "b" :: "c" :: "c" :: Nil).via(Deduplicate(2, map)).runWith(Sink.seq)
    result map { s =>
      s should contain theSameElementsInOrderAs ("a" :: "b" :: "c" :: Nil)
      map should have size 1
      map.get("b") shouldEqual MutableLong(1)
    }
  }

  it should "work with a custom registry" in {
    val treeMap = new util.TreeMap[String, MutableLong]()
    val result = Source("a" :: "a" :: "b" :: "b" :: "c" :: "c" :: Nil).via(Deduplicate(2, treeMap)).runWith(Sink.seq)
    result map { s =>
      s should contain theSameElementsInOrderAs ("a" :: "b" :: "c" :: Nil)
      treeMap shouldBe empty
    }
  }

  it should "work with a custom key function" in {
    val result = Source((1 -> "a") :: (1 -> "sameKey") ::
                        (2 -> "b") :: (2 -> "b") ::
                        (3 -> "c") :: (3 -> "c") :: Nil).
                via(Deduplicate((element: (Int, String)) => element._1, 2)).runWith(Sink.seq)
    result map { s =>
      s should contain theSameElementsInOrderAs ((1 -> "a") :: (2 -> "b") :: (3 -> "c") :: Nil)
    }
  }
}

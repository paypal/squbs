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

package org.squbs.marshallers.json

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.{HttpEntity, MediaTypes, MessageEntity}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.json4s._
import org.json4s.jackson.Serialization
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class Json4sJacksonSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import com.github.pjfanning.pekkohttpjson4s.Json4sSupport._

  implicit val system = ActorSystem("Json4sJacksonSpec")
  implicit val serialization = jackson.Serialization

  "NotTypeHints Example (case class)" should "have correct behaviour of read/write" in {
    implicit val formats = DefaultFormats.withHints(NoTypeHints)
    val playInfo = PlayerInfo("d", "k", 30)
    val entity = HttpEntity(MediaTypes.`application/json`, """{"firstName":"d","lastName":"k","age":30}""")
    Marshal(playInfo).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[PlayerInfo] map { _ shouldBe playInfo }
  }

  "NotTypeHints Example (case class contain the other case class)" should "have correct behaviour of read/write" in {
    implicit val formats = DefaultFormats.withHints(NoTypeHints)
    val name = Player("d", "k")
    val playInfo = PlayerInfo2(name, 30)
    val entity = HttpEntity(MediaTypes.`application/json`, """{"name":{"firstName":"d","lastName":"k"},"age":30}""")
    Marshal(playInfo).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[PlayerInfo2] map { _ shouldBe playInfo }
  }

  "ShortTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    implicit val formats = DefaultFormats.withHints(ShortTypeHints(classOf[Dog] :: classOf[Fish] :: Nil))
    val animals = Animals(Dog("pluto") :: Fish(1.2) :: Nil)
    val entity = HttpEntity(MediaTypes.`application/json`,
      """{"animals":[{"jsonClass":"Dog","name":"pluto"},{"jsonClass":"Fish","weight":1.2}]}""")
    Marshal(animals).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[Animals] map { _ shouldBe animals }
  }

  "FullTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    implicit val formats: Formats = Serialization.formats(FullTypeHints(classOf[Dog] :: classOf[Fish] :: Nil))
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val entity = HttpEntity(MediaTypes.`application/json`,
      """{"animals":[{"jsonClass":"org.squbs.marshallers.json.Dog","name":"lucky"},""" +
      """{"jsonClass":"org.squbs.marshallers.json.Fish","weight":3.4}]}""")
    Marshal(animals).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[Animals] map { _ shouldBe animals }
  }

  "Custom Example (inheritance)" should "have correct behaviour of read/write" in {
    implicit val format: Formats = Serialization.formats(
      FullTypeHints(List(classOf[Dog], classOf[Fish]), typeHintFieldName = "$type$"))
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val entity = HttpEntity(MediaTypes.`application/json`,
      """{"animals":[{"$type$":"org.squbs.marshallers.json.Dog","name":"lucky"},""" +
      """{"$type$":"org.squbs.marshallers.json.Fish","weight":3.4}]}""")
    Marshal(animals).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[Animals] map { _ shouldBe animals }
  }

  override protected def afterAll(): Unit = system.terminate()
}

case class Player(firstName: String, lastName: String)
case class PlayerInfo(firstName: String, lastName: String, age: Int)
case class PlayerInfo2(name: Player, age: Int)

trait Animal
case class Dog(name: String) extends Animal
case class Fish(weight: Double) extends Animal
case class Animals(animals: List[Animal])

/*
 *  Copyright 2015 PayPal
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

package org.squbs.httpclient.json

import org.scalatest.{Matchers, FlatSpec}
import org.json4s._
import native.Serialization._

class Json4sNativeSpec extends FlatSpec with Matchers{

  "NotTypeHints Example (case class)" should "have correct behaviour of read/write" in {
    import Json4sNativeNoTypeHintsProtocol._
    val playInfo = PlayerInfo("d", "k", 30)
    val jsonString = """{"firstName":"d","lastName":"k","age":30}"""
    write(playInfo) should be (jsonString)
    read[PlayerInfo](jsonString) should be (playInfo)
  }

  "NotTypeHints Example (case class contain the other case class)" should "have correct behaviour of read/write" in {
    import Json4sNativeNoTypeHintsProtocol._
    val name = Player("d", "k")
    val playInfo = PlayerInfo2(name, 30)
    val jsonString = """{"name":{"firstName":"d","lastName":"k"},"age":30}"""
    write(playInfo) should be (jsonString)
    read[PlayerInfo2](jsonString) should be (playInfo)
  }

  "ShortTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sNativeShortTypeHintsProtocolExample._
    val animals = Animals(Dog("pluto") :: Fish(1.2) :: Nil)
    val jsonString = """{"animals":[{"jsonClass":"Dog","name":"pluto"},{"jsonClass":"Fish","weight":1.2}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

  "FullTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sNativeFullTypeHintsProtocolExample._
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val jsonString = """{"animals":[{"jsonClass":"org.squbs.httpclient.json.Dog","name":"lucky"},""" +
      """{"jsonClass":"org.squbs.httpclient.json.Fish","weight":3.4}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

  "Custom Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sNativeCustomProtocolExample._
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val jsonString = """{"animals":[{"$type$":"org.squbs.httpclient.json.Dog","name":"lucky"},""" +
      """{"$type$":"org.squbs.httpclient.json.Fish","weight":3.4}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }
}

object Json4sNativeShortTypeHintsProtocolExample extends Json4sNativeShortTypeHintsProtocol {
  override def hints: List[Class[_]] = List(classOf[Dog], classOf[Fish])
}

object Json4sNativeFullTypeHintsProtocolExample extends Json4sNativeFullTypeHintsProtocol {
  override def hints: List[Class[_]] = List(classOf[Dog], classOf[Fish])
}

object Json4sNativeCustomProtocolExample extends Json4sNativeCustomProtocol {
  override implicit def json4sFormats: Formats = new Formats {
    val dateFormat = DefaultFormats.lossless.dateFormat
    override val typeHints = FullTypeHints(classOf[Fish] :: classOf[Dog] :: Nil)
    override val typeHintFieldName = "$type$"
  }
}
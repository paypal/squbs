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

package org.squbs.pipeline

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class RequestContextSpec
  extends TestKit(ActorSystem("RequestContextSpecSys"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    system.terminate()
  }

  it should "handle attributes correctly (withAttributes/removeAttributes/expanded variable args)" in {
    val attributeAdditions =
      List(
        "key1" -> "val1",
        "key2" -> 1,
        "key3" -> new Exception("Bad Val")
      )

    val rc1 = RequestContext(HttpRequest(), 0)
    val rc2 = rc1.withAttributes(attributeAdditions:_*)
    rc1.attributes should have size 0
    rc2.attributes should equal(attributeAdditions.toMap)

    val addedKeys = attributeAdditions.map(_._1).toSet
    val removeKeys = Set(addedKeys.head, addedKeys.last)
    val rc3 = rc2.removeAttributes("ibetternotexist" +: removeKeys.toList:_*)
    rc3.attributes.keys.toSet should equal(addedKeys -- removeKeys)
  }

  it should "handle attributes correctly (withAttributes/variable args)" in {
    val rc = RequestContext(HttpRequest(), 0)

    rc.withAttributes(
      "key1" -> "val1",
      "key2" -> 1
    ).attributes should equal(Map("key1" -> "val1", "key2" -> 1))

    rc.attributes should have size 0
  }

  it should "handle attributes correctly (withAttribute/removeAttribute)" in {
    val rc1 = RequestContext(HttpRequest(), 0)

    rc1.attributes should have size 0

    val rc2 = rc1.withAttribute("key1", "val1")
    rc1.attributes should have size 0 // Immutability
    rc2.attribute("key1") should be(Some("val1"))

    val rc3 = rc2.withAttribute("key2", 1).withAttribute("key3", new Exception("Bad Val"))
    rc3.attribute("key2") should be(Some(1))
    rc3.attribute[Exception]("key3").value.getMessage should be("Bad Val")
    rc3.attribute("key1") should be(Some("val1"))

    rc3.attributes should have size 3

    val rc4 = rc3.removeAttribute("key1")
    rc3.attributes should have size 3 // Immutability
    rc4.attributes should have size 2

    val rc5 = rc4.removeAttribute("notexists")
    rc5.attributes should have size 2
    rc5.attribute("key2") should be(Some(1))
    rc5.attribute[Exception]("key3").value.getMessage should be("Bad Val")
  }

  it should "handle headers correctly" in {
    val rc1 = RequestContext(HttpRequest(), 0)
    val rc2 = rc1.withRequestHeader(RawHeader("key1", "val1"))
    val rc3 = rc2.withRequestHeaders(RawHeader("key2", "val2"), RawHeader("key3", "val3"))

    rc3.request.headers should have size 3
    rc3.response should be(None)
    rc3.request.headers should contain(RawHeader("key1", "val1"))

    val rc4 = rc3.withResponse(Try(HttpResponse()))
    rc4.response.value.get.headers should have size 0
    val rc5 = rc4.withResponseHeader(RawHeader("key4", "val4"))
    val rc6 = rc5.withResponseHeaders(RawHeader("key5", "val5"), RawHeader("key6", "val6"))
    val rc7 = rc6.withResponseHeader(RawHeader("key7", "val7"))
    rc7.request.headers should have size 3
    rc7.response.value.get.headers should have size 4
    rc7.response.value.get.headers should contain(RawHeader("key4", "val4"))
  }

}

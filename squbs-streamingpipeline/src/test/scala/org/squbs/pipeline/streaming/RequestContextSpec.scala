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

package org.squbs.pipeline.streaming

import java.util

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.headers.RawHeader
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpecLike}
import org.scalatest.OptionValues._

import scala.util.Try

class RequestContextSpec extends TestKit(ActorSystem("RequestContextSpecSys")) with FlatSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    system.terminate()
  }

  it should "handle attributes correctly" in {
    val rc1 = RequestContext(HttpRequest(), 0)

    rc1.attributes should have size 0

    val rc2 = rc1.withAttributes(util.Arrays.asList(("key1" -> "val1")))
    rc1.attributes should have size 0 // Immutability
    rc2.attribute("key1") should be(Some("val1"))

    val rc3 = rc2 ++ ("key2" -> 1) ++ ("key3"-> new Exception("Bad Val"))
    rc3.attribute("key2") should be(Some(1))
    rc3.attribute[Exception]("key3").value.getMessage should be("Bad Val")
    rc3.attribute("key1") should be(Some("val1"))

    rc3.attributes should have size 3

    val rc4 = rc3.removeAttributes(util.Arrays.asList("key1"))
    rc3.attributes should have size 3 // Immutability
    rc4.attributes should have size 2

    val rc5 = rc4 -- ("notexists")
    rc5.attributes should have size 2
    rc5.attribute("key2") should be(Some(1))
    rc5.attribute[Exception]("key3").value.getMessage should be("Bad Val")
  }

  it should "handle headers correctly" in {
    val rc1 = RequestContext(HttpRequest(), 0)
    val rc2 = rc1.addRequestHeader(RawHeader("key1", "val1"))
    val rc3 = rc2.addRequestHeaders(RawHeader("key2", "val2"), RawHeader("key3", "val3"))

    rc3.request.headers should have size 3
    rc3.response should be(None)
    rc3.request.headers should contain(RawHeader("key1", "val1"))

    val rc4 = rc3.copy(response = Some(Try(HttpResponse())))
    rc4.response.value.get.headers should have size 0
    val rc5 = rc4.addResponseHeader(RawHeader("key4", "val4"))
    val rc6 = rc5.addResponseHeaders(RawHeader("key5", "val5"), RawHeader("key6", "val6"))
    val rc7 = rc6.addResponseHeader(RawHeader("key7", "val7"))
    rc7.request.headers should have size 3
    rc7.response.value.get.headers should have size 4
    rc7.response.value.get.headers should contain(RawHeader("key4", "val4"))
  }
}

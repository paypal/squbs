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

package org.squbs.samples.pubsub.actors

import org.scalatest.{FunSpec, Matchers}
import UnsignedHexUtil._
import akka.util.ByteString

class UnsignedHexUtilSpec extends FunSpec with Matchers {

  describe ("The unsigned hex utility") {
    it ("Should be able to parse 0") {
      val orig = 0l
      val uHex = orig.toHexString
      println(s"$orig = $uHex")
      uHex.uHexToLong should be (orig)
    }

    it ("Should be able to parse 10") {
      val orig = 10l
      val uHex = orig.toHexString
      println(s"$orig = $uHex")
      uHex.uHexToLong should be (orig)
    }

    it ("Should be able to parse 0 - 10") {
      val orig = -10l
      val uHex = orig.toHexString
      println(s"$orig = $uHex")
      uHex.uHexToLong should be (orig)
    }

    it ("Should be able to parse Long.MaxValue") {
      val orig = Long.MaxValue
      val uHex = orig.toHexString
      println(s"Long.MaxValue = $uHex")
      uHex.uHexToLong should be (orig)

    }

    it ("Should be able to parse Long.MaxValue - 10") {
      val orig = Long.MaxValue - 10l
      val uHex = orig.toHexString
      println(s"Long.MaxValue - 10 = $uHex")
      uHex.uHexToLong should be (orig)
    }

    it ("Should be able to parse Long.MinValue") {
      val orig = Long.MinValue
      val uHex = orig.toHexString
      println(s"Long.MinValue = $uHex")
      uHex.uHexToLong should be (orig)

    }

    it ("Should be able to parse Long.MinValue + 10") {
      val orig = Long.MinValue + 10l
      val uHex = orig.toHexString
      println(s"Long.MinValue + 10 = $uHex")
      uHex.uHexToLong should be (orig)
    }

    it ("Should be able to convert 0 to ByteString correctly") {
      val orig = 0l
      val b1 = orig.toUHexByteString
      val b2 = ByteString(orig.toHexString)
      b1 should be (b2)
    }

    it ("Should be able to convert 10 to ByteString correctly") {
      val orig = 10l
      val b1 = orig.toUHexByteString
      val b2 = ByteString(orig.toHexString)
      b1 should be (b2)
    }

    it ("Should be able to convert -10 to ByteString correctly") {
      val orig = -10l
      val b1 = orig.toUHexByteString
      val b2 = ByteString(orig.toHexString)
      b1 should be (b2)
    }

    it ("Should be able to convert Long.MaxValue to ByteString correctly") {
      val orig = Long.MaxValue
      val b1 = orig.toUHexByteString
      val b2 = ByteString(orig.toHexString)
      b1 should be (b2)
    }

    it ("Should be able to convert Long.MaxValue - 10 to ByteString correctly") {
      val orig = Long.MaxValue - 10l
      val b1 = orig.toUHexByteString
      val b2 = ByteString(orig.toHexString)
      b1 should be (b2)
    }

    it ("Should be able to convert Long.MinValue to ByteString correctly") {
      val orig = Long.MinValue
      val b1 = orig.toUHexByteString
      val b2 = ByteString(orig.toHexString)
      b1 should be (b2)
    }

    it ("Should be able to convert Long.MinValue + 10 to ByteString correctly") {
      val orig = Long.MinValue + 10l
      val b1 = orig.toUHexByteString
      val b2 = ByteString(orig.toHexString)
      b1 should be (b2)
    }
  }
}

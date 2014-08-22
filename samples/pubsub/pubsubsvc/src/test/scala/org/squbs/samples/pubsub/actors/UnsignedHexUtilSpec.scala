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

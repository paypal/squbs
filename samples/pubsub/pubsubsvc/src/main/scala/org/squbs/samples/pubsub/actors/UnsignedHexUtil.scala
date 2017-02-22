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

import akka.util.ByteString

object UnsignedHexUtil {

  /**
   * All possible chars for representing a number as a String
   */
  private[actors] final val digits = {
    val charDigits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
      'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z')
    charDigits map (_.toByte)
  }

  private def toUnsignedByteString(l: Long, shift: Int): ByteString = {
    val bufSize = 16
    val buf = new Array[Byte](bufSize)
    val radix: Int = 1 << shift
    val mask: Long = radix - 1
    var i = l
    var charPos: Int = bufSize
    do {
      charPos -= 1
      buf(charPos) = digits((i & mask).toInt)
      i >>>= shift
    } while (i != 0)
    ByteString.fromArray(buf, charPos, bufSize - charPos)
  }

  implicit class LongConverter (val l: Long) extends AnyVal {
    def toUHexByteString: ByteString = toUnsignedByteString(l, 4)
  }

  implicit class UHexConverter(val valueInUnsignedHex: String) extends AnyVal {
    /**
     * Returns a Long containing the least-significant 64 bits of the unsigned hexadecimal input.
     *
     * @return                        a { @code Long} containing the least-significant 64 bits of the value
     * @throws NumberFormatException  if the input { @link String} is empty or contains any non-hexadecimal characters
     */
    def uHexToLong: Long = {
      val hexLength = valueInUnsignedHex.length
      if (hexLength == 0) throw new NumberFormatException("""For input string: "" """)
      var i = {
        val start = hexLength - 16
        if (start > 0) start else 0
      }
      var value = 0l
      while (i < hexLength) {
        val ch: Char = valueInUnsignedHex.charAt(i)
        if (ch >= '0' && ch <= '9') value = (value << 4) | (ch - '0')
        else if (ch >= 'A' && ch <= 'F') value = (value << 4) | (ch - ('A' - 0xaL))
        else if (ch >= 'a' && ch <= 'f') value = (value << 4) | (ch - ('a' - 0xaL))
        else throw new NumberFormatException(s"""For input string: "$valueInUnsignedHex" """)
        i += 1
      }
      value
    }
  }
}

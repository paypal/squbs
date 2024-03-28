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
package org.squbs.pattern.stream

import org.apache.pekko.util.ByteString
import net.openhft.chronicle.wire.{WireIn, WireOut}

import scala.reflect._

object QueueSerializer {

  def apply[T: ClassTag](): QueueSerializer[T] = classTag[T] match {
    case t if classOf[ByteString] == t.runtimeClass =>
      new ByteStringSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[AnyRef] isAssignableFrom t.runtimeClass =>
      new ObjectSerializer[T]
    case t if classOf[Long] == t.runtimeClass =>
      new LongSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[Int] == t.runtimeClass =>
      new IntSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[Short] == t.runtimeClass =>
      new ShortSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[Byte] == t.runtimeClass =>
      new ByteSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[Char] == t.runtimeClass =>
      new CharSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[Double] == t.runtimeClass =>
      new DoubleSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[Float] == t.runtimeClass =>
      new FloatSerializer().asInstanceOf[QueueSerializer[T]]
    case t if classOf[Boolean] == t.runtimeClass =>
      new BooleanSerializer().asInstanceOf[QueueSerializer[T]]
    case t => throw new ClassCastException("Unsupported Type: " + t)
  }
}

trait QueueSerializer[T] {

  /**
    * Reads an element from the wire.
    *
    * @param wire The wire.
    * @return The element as an option, or None if no element is available.
    */
  def readElement(wire: WireIn): Option[T]

  /**
    * Writes an element to the wire.
    *
    * @param element The element to be written.
    * @param wire The wire.
    */
  def writeElement(element: T, wire: WireOut): Unit
}

class ByteStringSerializer extends QueueSerializer[ByteString] {

  def writeElement(element: ByteString, wire: WireOut): Unit = {
    val bb = element.asByteBuffer
    val output = new Array[Byte](bb.remaining)
    bb.get(output)
    wire.write().bytes(output)
  }

  def readElement(wire: WireIn): Option[ByteString] =
  // TODO: wire.read() may need some optimization. It uses a StringBuilder underneath
    Option(wire.read().bytes) map (ByteString(_))
}

class ObjectSerializer[T : ClassTag] extends QueueSerializer[T] {

  val clazz: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]

  def readElement(wire: WireIn): Option[T] = Option(wire.read().`object`(clazz))

  def writeElement(element: T, wire: WireOut): Unit = wire.write().`object`(clazz, element)
}

class LongSerializer extends QueueSerializer[Long] {

  def readElement(wire: WireIn): Option[Long] = Option(wire.read().int64)

  def writeElement(element: Long, wire: WireOut): Unit = wire.write().int64(element)
}

class IntSerializer extends QueueSerializer[Int] {

  def readElement(wire: WireIn): Option[Int] = Option(wire.read().int32)

  def writeElement(element: Int, wire: WireOut): Unit = wire.write().int32(element)
}

class ShortSerializer extends QueueSerializer[Short] {

  def readElement(wire: WireIn): Option[Short] = Option(wire.read().int16)

  def writeElement(element: Short, wire: WireOut): Unit = wire.write().int16(element)
}

class ByteSerializer extends QueueSerializer[Byte] {

  def readElement(wire: WireIn): Option[Byte] = Option(wire.read().int8)

  def writeElement(element: Byte, wire: WireOut): Unit = wire.write().int8(element)
}

class CharSerializer extends QueueSerializer[Char] {

  def readElement(wire: WireIn): Option[Char] = Option(wire.read().int16) map (_.toChar)

  def writeElement(element: Char, wire: WireOut): Unit = wire.write().int16(element.toShort)
}

class DoubleSerializer extends QueueSerializer[Double] {

  def readElement(wire: WireIn): Option[Double] = Option(wire.read().float64())

  def writeElement(element: Double, wire: WireOut): Unit = wire.write().float64(element)
}

class FloatSerializer extends QueueSerializer[Float] {

  def readElement(wire: WireIn): Option[Float] = Option(wire.read().float64) map (_.toFloat)

  def writeElement(element: Float, wire: WireOut): Unit = wire.write().float64(element)
}

class BooleanSerializer extends QueueSerializer[Boolean] {

  def readElement(wire: WireIn): Option[Boolean] = Option(wire.read().bool())

  def writeElement(element: Boolean, wire: WireOut): Unit = wire.write().bool(element)
}

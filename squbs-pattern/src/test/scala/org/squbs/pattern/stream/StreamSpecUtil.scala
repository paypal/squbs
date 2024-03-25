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

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

import org.apache.pekko.stream.ThrottleMode
import org.apache.pekko.stream.scaladsl._
import com.typesafe.config.ConfigFactory
import net.openhft.chronicle.wire.{WireIn, WireOut}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.jdk.CollectionConverters._
import scala.util.Random

object StreamSpecUtil {
  val elementCount = 100000
  val failTestAt = elementCount * 3 / 10
  val elementsAfterFail = 100
  val flowRate = 1000
  val flowUnit = 10 millisecond
  val burstSize = 500
}

class StreamSpecUtil[T, S](outputPort: Int = 1) {

  import StreamSpecUtil._
  val outputPorts = outputPort
  val tempPath: File = Files.createTempDirectory("persistent_queue").toFile
  val totalProcessed = elementCount + elementsAfterFail

  val config = ConfigFactory.parseMap {
    Map(
      "persist-dir" -> s"${tempPath.getAbsolutePath}",
      "output-ports" -> s"$outputPorts",
      "roll-cycle" -> "TEST_SECONDLY".toLowerCase()
    ).asJava
  }

  val in = Source(1 to elementCount)
  lazy val atomicCounter = Vector.tabulate(outputPorts)(_ => new AtomicInteger(0))
  lazy val flowCounter = Flow[Any].map(_ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
  lazy val merge = Merge[S](outputPorts)
  lazy val throttle = Flow[S].throttle(flowRate, flowUnit, burstSize, ThrottleMode.shaping)
  lazy val throttleMore = Flow[S].throttle(flowRate * 9 / 10, flowUnit, burstSize, ThrottleMode.shaping)
  lazy val head = Sink.head[S]
  lazy val last = Sink.last[S]
  val minRandom = 100
  lazy val random = Random.nextInt(elementCount - minRandom - 1) + minRandom
  lazy val filterCounter = new AtomicInteger(0)
  lazy val filterARandomElement = Flow[Event[T]].map(e => (e, filterCounter.incrementAndGet())).filter(_._2 != random).map(_._1)

  def commitCounter(outputPortId: Int) = atomicCounter(outputPortId).incrementAndGet()

  def clean() = delete(tempPath)

  private def delete(file: File): Unit = {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(delete)
    file.delete
  }
}

case class Person(name: String, age: Int)

class PersonSerializer extends QueueSerializer[Person] {

  override def readElement(wire: WireIn): Option[Person] = {
    for {
      name <- Option(wire.read().`object`(classOf[String]))
      age <- Option(wire.read().int32)
    } yield { Person(name, age) }
  }

  override def writeElement(element: Person, wire: WireOut): Unit = {
    wire.write().`object`(classOf[String], element.name)
    wire.write().int32(element.age)
  }
}

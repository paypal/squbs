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
package org.squbs.pattern.stream

import java.io.{File, FileNotFoundException}
import java.nio.file.Files

import akka.util.ByteString
import org.scalatest.OptionValues._
import org.scalatest._

class PersistentQueueSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val tempPath = Files.createTempDirectory("persistent_queue")
  implicit val serializer = QueueSerializer[ByteString]()
  val queue = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile))

  def delete(file: File) {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(delete(_))
    file.delete
  }

  override def afterAll() {
    queue.close()
    delete(tempPath.toFile)
  }

  it should "dequeue the same entry as it enqueues" in {
    val element = ByteString("Hello!")
    queue.enqueue(element)
    val elementOption = queue.dequeue
    elementOption.value shouldBe element
  }

  it should "dequeue each entry as it enqueues, one-by-one" in {
    for { i <- 1 to 1000000 } {
      val element = ByteString(s"Hello $i")
      queue.enqueue(element)
      val elementOption = queue.dequeue
      elementOption.value shouldBe element
    }
  }

  it should "dequeue each entry as it enqueues, for all entries" in {
    for { i <- 1 to 1000000 } {
      val element = ByteString(s"Hello $i")
      queue.enqueue(element)
    }

    for { i <- 1 to 1000000 } {
      val element = ByteString(s"Hello $i")
      val elementOption = queue.dequeue
      elementOption.value shouldBe element
    }
  }

  it should "dequeue each entry as it enqueues, even if the stream is reopened" in {

    val tempPath2 = Files.createTempDirectory("persistent_queue")

    val queue2 = new PersistentQueue[ByteString](QueueConfig(tempPath2.toFile))
    for { i <- 1 to 700000 } {
      val element = ByteString(s"Hello $i")
      queue2.enqueue(element)
    }
    queue2.close()

    val queue3 = new PersistentQueue[ByteString](QueueConfig(tempPath2.toFile))
    for { i <- 1 to 500000 } {
      val element = ByteString(s"Hello $i")
      val elementOption = queue3.dequeue
      elementOption.value shouldBe element
    }
    queue3.close()

    val queue4 = new PersistentQueue[ByteString](QueueConfig(tempPath2.toFile))
    for { i <- 700001 to 1000000 } {
      val element = ByteString(s"Hello $i")
      queue4.enqueue(element)
    }
    // We only read 500000, so there should be 500000 left.
    queue4.close()

    val queue5 = new PersistentQueue[ByteString](QueueConfig(tempPath2.toFile))
    for { i <- 500001 to 1000000 } {
      val element = ByteString(s"Hello $i")
      val elementOption = queue5.dequeue
      elementOption.value shouldBe element
    }
    queue5.close()
  }

  it should "throw the appropriate exception if queue file cannot be created" in {
    val badPath = Files.createTempFile("testException", "test")
    a [FileNotFoundException] should be thrownBy new PersistentQueue[ByteString](QueueConfig(badPath.toFile))
    Files.delete(badPath)
  }
}

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
import net.openhft.chronicle.queue.RollCycles
import net.openhft.chronicle.queue.impl.single.DirectoryListing
import org.scalatest.OptionValues._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.{File, FileNotFoundException}
import java.nio.file.Files
import scala.annotation.tailrec

class PersistentQueueSpec extends AnyFlatSpec with Matchers with PrivateMethodTester {

  implicit val serializer = QueueSerializer[ByteString]()

  def delete(file: File): Unit = {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(delete)
    file.delete
  }

  it should "dequeue the same entry as it enqueues" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val queue = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile))
    val element = ByteString("Hello!")
    queue.enqueue(element)
    val elementOption = queue.dequeue()
    elementOption.value.entry shouldBe element
    queue.close()
    delete(tempPath.toFile)
  }

  it should "dequeue the same entry as it enqueues for n outputPorts" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val queue = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile, outputPorts = 3))
    val element = ByteString("Hello!")
    queue.enqueue(element)
    (0 until queue.totalOutputPorts) foreach { outputPortId =>
      val elementOption = queue.dequeue(outputPortId)
      elementOption.value.entry shouldBe element
    }
    queue.close()
    delete(tempPath.toFile)
  }

  it should "dequeue each entry as it enqueues, one-by-one" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val queue = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile))
    for { i <- 1 to 1000000 } {
      val element = ByteString(s"Hello $i")
      queue.enqueue(element)
      val elementOption = queue.dequeue()
      elementOption.value.entry shouldBe element
    }
    queue.close()
    delete(tempPath.toFile)
  }

  it should "dequeue each entry as it enqueues, one-by-one for n outputPorts" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val queue = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile, outputPorts = 3))
    for { i <- 1 to 1000000 } {
      val element = ByteString(s"Hello $i")
      queue.enqueue(element)
      (0 until queue.totalOutputPorts) foreach { outputPortId =>
        val elementOption = queue.dequeue(outputPortId)
        elementOption.value.entry shouldBe element
      }
    }
    queue.close()
    delete(tempPath.toFile)
  }

  it should "dequeue each entry as it enqueues, for all entries" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val queue = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile, outputPorts = 2))
    for { i <- 1 to 1000000 } {
      val element = ByteString(s"Hello $i")
      queue.enqueue(element)
    }

    for { i <- 1 to 1000000 } {
      val element = ByteString(s"Hello $i")
      val elementOption = queue.dequeue()
      elementOption.value.entry shouldBe element
    }
    queue.close()
    delete(tempPath.toFile)
  }

  it should "dequeue each entry as it enqueues, even if the stream is reopened" in {

    val tempPath = Files.createTempDirectory("persistent_queue")

    val queue2 = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile))
    for { i <- 1 to 700000 } {
      val element = ByteString(s"Hello $i")
      queue2.enqueue(element)
    }
    queue2.close()

    val queue3 = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile))
    for { i <- 1 to 500000 } {
      val element = ByteString(s"Hello $i")
      val elementOption = queue3.dequeue()
      queue3.commit(0, elementOption.value.index)
      elementOption.value.entry shouldBe element
    }
    queue3.close()

    val queue4 = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile))
    for { i <- 700001 to 1000000 } {
      val element = ByteString(s"Hello $i")
      queue4.enqueue(element)
    }
    // We only read 500000, so there should be 500000 left.
    queue4.close()

    val queue5 = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile))
    for { i <- 500001 to 1000000 } {
      val element = ByteString(s"Hello $i")
      val elementOption = queue5.dequeue()
      queue5.commit(0, elementOption.value.index)
      elementOption.value.entry shouldBe element
    }
    queue5.close()
    delete(tempPath.toFile)
  }

  it should "throw the appropriate exception if queue file cannot be created" in {
    val badPath = Files.createTempFile("testException", "test")
    a [FileNotFoundException] should be thrownBy new PersistentQueue[ByteString](QueueConfig(badPath.toFile))
    Files.delete(badPath)
  }

  it should "clean up buffer resources automatically" in {
    val tempPath = Files.createTempDirectory("persistent_queue")
    val queue = new PersistentQueue[ByteString](QueueConfig(tempPath.toFile, rollCycle = RollCycles.TEST_SECONDLY))

    def dataFiles = tempPath.toFile.listFiles()
      .toList.filterNot(file => file.getName == "tailer.idx" || file.getName == DirectoryListing.DIRECTORY_LISTING_FILE)

    addToQueue(0, 0)

    @tailrec
    def addToQueue(size: Int, i: Int): Unit = {
      if (size < 4) {
        val element = ByteString(s"Hello $i")
        queue.enqueue(element)
        addToQueue(dataFiles.size, i + 1)
      }
    }

    dataFiles should not be empty
    val deleteOlderFiles = PrivateMethod[(Int, File)]('deleteOlderFiles)

    // case 1: it should not cleanup unprocessed queue file
    val parser = queue.fileIdParser
    val oldestFile = dataFiles.minBy(parser.toLong)
    queue.resourceManager invokePrivate deleteOlderFiles(0, dataFiles.filter(_ != oldestFile).head)
    dataFiles.filter(f => f == oldestFile) shouldBe empty
    dataFiles should not be empty

    // case 2: it should cleanup processed queue file, except last file
    val newestFile = dataFiles.maxBy(parser.toLong)
    queue.resourceManager invokePrivate deleteOlderFiles(0, newestFile)
    dataFiles.filterNot(f => f == newestFile) shouldBe empty
    queue.close()
    delete(tempPath.toFile)
  }
}

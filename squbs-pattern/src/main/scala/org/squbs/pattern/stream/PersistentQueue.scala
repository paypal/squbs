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

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import net.openhft.chronicle.bytes.MappedBytesStore
import net.openhft.chronicle.core.OS
import net.openhft.chronicle.queue.ChronicleQueueBuilder
import net.openhft.chronicle.queue.impl.RollingResourcesCache.Resource
import net.openhft.chronicle.wire.{ReadMarshallable, WireIn, WireOut, WriteMarshallable}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

case class Entry[T](index: Long, entry: T)
case class Event[T](outputPortId: Int, commitOffset: Long, entry: T)
/**
  * Persistent queue using Chronicle Queue as implementation.
  *
  * @tparam T The type of elements to be stored in the queue.
  */
class PersistentQueue[T](config: QueueConfig, onCommitCallback: Int => Unit = (x => {}))(implicit val serializer: QueueSerializer[T]) {

  def this(config: Config)(implicit serializer: QueueSerializer[T]) = this(QueueConfig.from(config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T]) = this(QueueConfig(persistDir))

  def withOnCommitCallback(onCommitCallback: Int => Unit) = new PersistentQueue[T](config, onCommitCallback)

  import config._
  if (!persistDir.isDirectory && !persistDir.mkdirs())
    throw new FileNotFoundException(persistDir.getAbsolutePath)

  val logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val queue = ChronicleQueueBuilder
    .single(persistDir.getAbsolutePath)
    .wireType(wireType)
    .rollCycle(rollCycle)
    .blockSize(blockSize.toInt)
    .indexSpacing(indexSpacing)
    .indexCount(indexCount)
    .build()

  private val appender = queue.createAppender
  private val cache = QueueResource.resourceCache(queue)
  private val reader = Vector.tabulate(outputPorts)(_ => queue.createTailer)

  private val path = new File(persistDir, "tailer.idx")

  private var written = false
  private var indexMounted = false
  private var indexFile: IndexFile = _
  private var indexStore: MappedBytesStore = _
  private var closed = false
  private val cycle = Array.ofDim[Int](outputPorts)
  private val lastCommitIndex = Array.ofDim[Long](outputPorts)

  val totalOutputPorts = outputPorts
  val autoCommit = config.autoCommit

  private def mountIndexFile(): Unit = {
    indexFile = IndexFile.of(path, OS.pageSize())
    indexStore = indexFile.acquireByteStore(0L)
    indexMounted = true
  }

  if (path.isFile) {
    mountIndexFile()
    0 until outputPorts foreach { outputPortId =>
      val startIdx = read(outputPortId)
      logger.info("Setting idx for outputPort {} - {}", outputPortId.toString, startIdx.toString)
      reader(outputPortId).moveToIndex(startIdx)
      dequeue(outputPortId) // dequeue the first read element
      lastCommitIndex(outputPortId) = startIdx
      cycle(outputPortId) = queue.rollCycle().toCycle(startIdx)
    }
  }

  /**
    * Adds an element to the queue.
    *
    * @param element The element to be added.
    */
  def enqueue(element: T): Unit = {
    appender.writeDocument(new WriteMarshallable {
      override def writeMarshallable(wire: WireOut): Unit = serializer.writeElement(element, wire)
    })
    written = true
  }

  /**
    * Fetches the first element from the queue and removes it from the queue.
    *
    * @return The first element in the queue, or None if the queue is empty.
    */
  def dequeue(outputPortId: Int = 0): Option[Entry[T]] = {
    var output: Option[Entry[T]] = None
    if (reader(outputPortId).readDocument(new ReadMarshallable {
      override def readMarshallable(wire: WireIn): Unit =
        output = {
          val element = serializer.readElement(wire)
          val index = reader(outputPortId).index
          element map { e => Entry[T](index, e) }
        }
    })) output else None
  }

  /**
    * Commits the queue's index, this index is mounted when
    * the queue is initialized next time
    *
    * @param outputPortId The id of the output port
    * @param index to be committed for next read
    */
  def commit(outputPortId: Int, index: Long): Unit = {
    verifyCommitOrder(outputPortId, index)
    if (!indexMounted) mountIndexFile()
    indexStore.writeLong(outputPortId << 3, index)
    onCommitCallback(outputPortId)
  }

  private def verifyCommitOrder(outputPortId: Int, index: Long): Unit = {
    val lastCommit = lastCommitIndex(outputPortId)
    if(index == lastCommit + 1) lastCommitIndex(outputPortId) = index
    else {
      val newCycle = queue.rollCycle().toCycle(index)
      if(newCycle == cycle(outputPortId) + 1 || lastCommit == 0) {
        cycle(outputPortId) = newCycle
        lastCommitIndex(outputPortId) = index
      } else {
        config.commitOrderPolicy match {
          case Lenient =>
            logger.warn(s"Missing or out of order commits.  previous: ${lastCommitIndex(outputPortId)} latest: $index cycle: ${cycle(outputPortId)}")
            lastCommitIndex(outputPortId) = index
          case Strict =>
            val msg = s"Missing or out of order commits.  previous: ${lastCommitIndex(outputPortId)} latest: $index cycle: ${cycle(outputPortId)}"
            logger.error(msg)
            // Not closing the queue here as `Supervision.Decider` might resume the stream.
            throw new CommitOrderException(msg, lastCommitIndex(outputPortId), index, cycle(outputPortId))
        }
      }
    }
  }

  // Reads the given outputPort's queue index
  private[stream] def read(outputPortId: Int) : Long = {
    if (!indexMounted) mountIndexFile()
    indexStore.readLong(outputPortId << 3)
  }

  private def minIndex() = Iterator.tabulate(outputPorts)(outputPortId => read(outputPortId)).min

  /**
    * Closes the queue and all its persistent storage.
    */
  def close(): Unit = {
    closed = true
    if (written) appender.close()
    reader.foreach { m => m.close() }
    queue.close()
    Option(indexStore) foreach { store => if (store.refCount > 0) store.close() }
    Option(indexFile) foreach { file => file.close() }
  }

  private[stream] def isClosed = closed

  /**
    * Removes processed queue files based on
    * min read index from all outputPorts
    */
  def clearStorage(): Unit = {
    val reader = queue.createTailer(); reader.moveToIndex(minIndex())
    val current = cache.resourceFor(reader.cycle)
    def first() = cache.resourceFor(reader.toStart.cycle)

    @tailrec
    def delete(previous: Resource) : Unit = {
      if (current.path != previous.path) {
        previous.path.delete()
        delete(first())
      }
    }
    delete(first())
  }
}

class CommitOrderException(message: String, previousIndex: Long, lastIndex: Long, cycle: Int) extends RuntimeException(message)
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
import net.openhft.chronicle.bytes.MappedBytesStore
import net.openhft.chronicle.core.OS
import net.openhft.chronicle.queue.ChronicleQueueBuilder
import net.openhft.chronicle.wire.{ReadMarshallable, WireIn, WireOut, WriteMarshallable}
/**
  * Persistent queue using Chronicle Queue as implementation.
  *
  * @tparam T The type of elements to be stored in the queue.
  */
class PersistentQueue[T](config: QueueConfig)(implicit val serializer: QueueSerializer[T]) {

  def this(config: Config)(implicit serializer: QueueSerializer[T]) = this(QueueConfig.from(config))

  def this(persistDir: File)(implicit serializer: QueueSerializer[T]) = this(QueueConfig(persistDir))

  import config._
  if (!persistDir.isDirectory && !persistDir.mkdirs())
    throw new FileNotFoundException(persistDir.getAbsolutePath)

  private val queue = ChronicleQueueBuilder
    .single(persistDir.getAbsolutePath)
    .wireType(wireType)
    .rollCycle(rollCycle)
    .blockSize(blockSize.toInt)
    .indexSpacing(indexSpacing)
    .indexCount(indexCount)
    .build()

  private val appender = queue.createAppender
  private val reader = queue.createTailer

  private val path = new File(persistDir, "tailer.idx")

  private var written = false
  private var indexMounted = false
  private var indexFile: IndexFile = _
  private var indexStore: MappedBytesStore = _

  private def mountIndexFile(): Unit = {
    indexFile = IndexFile.of(path, OS.pageSize())
    indexStore = indexFile.acquireByteStore(0L)
    indexMounted = true
  }

  if (path.isFile) {
    mountIndexFile()
    val startIdx = indexStore.readLong(0L)
    reader.moveToIndex(startIdx)
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
  def dequeue: Option[T] = {
    var output: Option[T] = None
    if (reader.readDocument(new ReadMarshallable {
      override def readMarshallable(wire: WireIn): Unit = output = serializer.readElement(wire)
    })) {
      if (!indexMounted) mountIndexFile()
      indexStore.writeLong(0L, reader.index)
      output
    } else None
  }

  /**
    * Closes the queue and all its persistent storage.
    */
  def close(): Unit = {
    if (written) appender.close()
    reader.close()
    queue.close()
    Option(indexStore) foreach { store => if (store.refCount > 0) store.close() }
    Option(indexFile) foreach { file => file.close() }
  }
}
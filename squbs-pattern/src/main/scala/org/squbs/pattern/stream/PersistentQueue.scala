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

import akka.util.ByteString
import net.openhft.chronicle.bytes.MappedBytesStore
import net.openhft.chronicle.core.OS
import net.openhft.chronicle.queue.ChronicleQueueBuilder
import net.openhft.chronicle.wire.{ReadMarshallable, WireIn, WireOut, WriteMarshallable}

/**
  * Creates a persistent queue. This implementation is based on Chronicle Queue.
  *
  * @param persistDir The directory where this queue should be persisted.
  */
class PersistentQueue(persistDir: File) {

  if (!persistDir.isDirectory && !persistDir.mkdirs()) throw new FileNotFoundException(persistDir.getAbsolutePath)

  private val queue = ChronicleQueueBuilder.single(persistDir.getAbsolutePath).build()
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
  def enqueue(element: ByteString): Unit = {
    appender.writeDocument(new WriteMarshallable {
      override def writeMarshallable(wire: WireOut): Unit = {
        val bb = element.asByteBuffer
        val output = new Array[Byte](bb.remaining)
        bb.get(output)
        wire.write().bytes(output)
      }
    })
    written = true
  }

  /**
    * Fetches the first element from the queue and removes it from the queue.
    *
    * @return The first element in the queue, or None if the queue is empty.
    */
  def dequeue: Option[ByteString] = {
    var output: Option[ByteString] = None
    if (reader.readDocument(new ReadMarshallable {
      override def readMarshallable(wire: WireIn): Unit = {
        // TODO: wire.read() may need some optimization. It uses a StringBuilder underneath
        output = Option(wire.read().bytes) map (ByteString(_))
      }
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

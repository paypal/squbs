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

import org.squbs.samples.pubsub.messages.Publish
import scala.collection.mutable
import spray.http.{HttpData, MessageChunk}
import akka.util.ByteString
import UnsignedHexUtil._

object EventCache {

  def apply(cacheSize: Int, lastEventOnConnect: Boolean): EventCache = {
    val effectiveCacheSize = if (lastEventOnConnect && cacheSize < 1) 1 else cacheSize
    if (effectiveCacheSize > 1) new Cache(effectiveCacheSize, lastEventOnConnect)
    else if (effectiveCacheSize == 1) new OneCache(lastEventOnConnect)
    else new NoCache
  }
}

sealed trait EventCache {
  val lastEventOnConnect: Boolean
  def storeAndFormat(evt: Publish): MessageChunk
  def eventsOnConnect(lastEvent: Option[String]): Iterator[MessageChunk]
}

class Cache(size: Int, val lastEventOnConnect: Boolean) extends EventCache {

  private[actors] val cache = mutable.Queue.empty[(Long, MessageChunk)]
  private[actors] var eventId = 0l
  private[actors] val overFlowThreshold = Long.MaxValue - size

  def storeAndFormat(evt: Publish) = {
    eventId += 1
    val messageChunk = MessageChunk(HttpData(SSESupport.toSSE(evt.eventType, eventId, evt.content)))
    while (cache.size >= size) cache.dequeue()
    cache += ((eventId, messageChunk))
    messageChunk
  }

  // Consider the case where we have an number overflow. The new id may be far below the last.
  // We detect this condition by seeing the head much lower than the last. Also, the last should be close to
  // Long.MaxValue. For instance, it should be higher than Long.MaxValue - size.
  private[actors] def findFromLastEvent(lastEventId: Long): Iterator[MessageChunk] = {
    if (cache.isEmpty) return Iterator.empty
    val firstCachedId = cache.head._1
    if (firstCachedId < overFlowThreshold) { // Normal case, really most of the time.
      if (lastEventId < firstCachedId) return cache.toIterator map (_._2)
      // Example: size = 7
      // EventIds 6, 7, 8, 9, 10, 11, 12
      // lastEventId = 8
      // offset = 8 - 6 + 1 = 3
      // send events 9, 10, 11, 12 - don't send 8 itself
      // This is the cache dropping the first n events where n = offset.
    }
    else { // We're near the overflow zone. Thread carefully. (Hope this code will never be exercised)
      // For easy comprehension, lets assume MaxValue = 100, after that the id will jump to -100.
      // (You may argue it should be 99 jumping to -100, but I just want to keep it easy)
      // Lets try a cache size of 10.
      // overflowThreshold would be 90.
      // Once event id 100 goes out, the cache will have 91, 92,..., 100.
      // If request comes in with last event less than 90, we just send the whole cache.
      // But remember, -100, -99, and so forth are in fact the overflowed and are greater numbers. So they cannot be
      // treated as a lower number. They are actually a higher number. So we can just send them an empty iterator.
      // In this circumstance, we consider anything below 0 an event id from the future.
      // Next, event id -100 goes out. The cache will have 92, 93, ..., 100, -100.
      // Lets try another one. event id -99 goes out. The cache will have 93, 94, ..., 100, -100, -99.
      // Here, if lastEventId is -100, But lastEventId - firstCachedId causes an underflow and thus results
      // in the correct offset. So we really don't have to handle it differently.
      if (lastEventId > 0 && lastEventId < firstCachedId) return cache.toIterator map (_._2)
    }
    val offset = 1 + lastEventId - firstCachedId
    if (offset < size)  cache.toIterator drop offset.toInt map (_._2)
    else Iterator.empty
  }


  def eventsOnConnect(lastEvent: Option[String]) = {
    lastEvent match {
      case Some(lastEventId) => findFromLastEvent(lastEventId.uHexToLong)
      case None if lastEventOnConnect => (cache.lastOption map (_._2)).toIterator
      case None => Iterator.empty
    }
  }
}

class OneCache(val lastEventOnConnect: Boolean) extends EventCache {

  private[actors] var cache: Option[(Long, MessageChunk)] = None
  private[actors] var eventId = 0l

  def storeAndFormat(evt: Publish) = {
    eventId += 1
    val messageChunk = MessageChunk(HttpData(SSESupport.toSSE(evt.eventType, eventId, evt.content)))
    cache = Some((eventId, messageChunk))
    messageChunk
  }

  def eventsOnConnect(lastEvent: Option[String]) = {
    lastEvent match { // If lastEvent is the one in cache, we don't send. Otherwise send.
      case Some(lastEventId) if cache exists (_._1 == lastEventId.uHexToLong) => Iterator.empty
      case Some(lastEventId) => cache.toIterator map (_._2)
      case None =>
        if (lastEventOnConnect) cache.toIterator map (_._2)
        else Iterator.empty
    }
  }
}

class NoCache extends EventCache {

  val lastEventOnConnect = false

  def storeAndFormat(evt: Publish) = MessageChunk(HttpData(SSESupport.toSSE(evt.eventType, evt.content)))

  def eventsOnConnect(lastEvent: Option[String]) = Iterator.empty
}

object SSESupport {
  private val event = ByteString("event: ")
  private val id = ByteString("\nid: ")
  private val data = ByteString("\ndata: ")
  private val endEvent = ByteString("\n\n")

  def toSSE(evtType: String, content: ByteString): ByteString =
    event ++ ByteString(evtType) ++ data ++ content ++ endEvent

  def toSSE(evtType: String, evtId: Long, content: ByteString): ByteString =
    event ++ ByteString(evtType) ++ id ++ evtId.toUHexByteString ++ data ++ content ++ endEvent

  val streamEnd = ByteString("event: stream_end\ndata: End of stream\n\n")
}

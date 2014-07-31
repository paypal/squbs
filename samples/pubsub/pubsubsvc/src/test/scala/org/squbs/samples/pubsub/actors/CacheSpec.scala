package org.squbs.samples.pubsub.actors

import org.scalatest.{Matchers, FunSpec}
import org.squbs.samples.pubsub.messages.Publish
import akka.util.ByteString
import spray.http.{HttpCharsets, HttpData, MessageChunk}
import java.nio.charset.Charset


class CacheSpec extends FunSpec with Matchers {

  describe ("The event cache") {

    it ("Should not have size larger than the given size") {
      val cache = new Cache(10, lastEventOnConnect = false)
      cache.eventId = 10
      for (i <- 0 until 12)
        cache.storeAndFormat(Publish(null, "cat", "channel", "testEvent", None, ByteString("Test Content " + i)))
      val queue = cache.cache
      queue should have size 10
    }

    it ("Should return the newest event on connect when enabled") {
      val cache = new Cache(10, lastEventOnConnect = true)
      cache.eventId = 10
      for (i <- 0 until 12)
        cache.storeAndFormat(Publish(null, "cat", "channel", "testEvent", None, ByteString("Test Content " + i)))
      val lastEvent = cache.eventsOnConnect(None).next().data.asString(HttpCharsets.`UTF-8`)
      val templateEvent = HttpData(SSESupport.toSSE("testEvent", 22, ByteString("Test Content 11"))).
        asString(HttpCharsets.`UTF-8`)
      println("LastEvent:\n" + lastEvent)
      println("TemplateEvent:\n" + templateEvent)
      lastEvent should be (templateEvent)
    }
    
    it ("Should return the whole cache if last event id is below all cached event ids") {
      val cache = new Cache(10, lastEventOnConnect = false)
      cache.eventId = 10
      for (i <- 0 until 10)
        cache.storeAndFormat(Publish(null, "cat", "channel", "testEvent", None, ByteString("Test Content " + i)))
      val events = cache.findFromLastEvent(9)
      events should have size 10
    }

    it ("Should return the partial cache if last event id is within cached event ids") {
      val cache = new Cache(10, lastEventOnConnect = false)
      cache.eventId = 10
      for (i <- 0 until 10)
        cache.storeAndFormat(Publish(null, "cat", "channel", "testEvent", None, ByteString("Test Content " + i)))
      val events = cache.findFromLastEvent(12)
      events should have size 8
    }

    it ("Should return the partial cache if last event id is large positive within cached event ids") {
      val cache = new Cache(10, lastEventOnConnect = false)
      cache.eventId = Long.MaxValue - 5
      for (i <- 0 until 10)
        cache.storeAndFormat(Publish(null, "cat", "channel", "testEvent", None, ByteString("Test Content " + i)))
      val events = cache.findFromLastEvent(Long.MaxValue - 3)
      events should have size 8
    }

    it ("Should return the partial cache if last event id is low negative within cached event ids") {
      val cache = new Cache(10, lastEventOnConnect = false)
      cache.eventId = Long.MaxValue - 5
      for (i <- 0 until 10)
        cache.storeAndFormat(Publish(null, "cat", "channel", "testEvent", None, ByteString("Test Content " + i)))
      val events = cache.findFromLastEvent(Long.MinValue + 1)
      events should have size 3
    }
  }
}

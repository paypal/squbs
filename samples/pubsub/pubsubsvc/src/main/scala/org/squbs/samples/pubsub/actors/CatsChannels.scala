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

package org.squbs.samples.pubsub.actors

import akka.actor._
import akka.event.LoggingAdapter
import scala.collection.mutable
import scala.concurrent.duration._
import spray.can.Http
import spray.http._
import spray.http.ContentTypes._
import spray.http.HttpHeaders.`Content-Type`
import spray.http.StatusCodes._
import spray.routing.RequestContext
import org.squbs.unicomplex.MediaTypeExt._
import org.squbs.samples.pubsub.messages._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.MapModule

object MapProps {

  final val descKey = "description"

  final val ownerKey = "owner"

  final val catKey = "category-key"

  final val channelKey = "channel-key"

  final val maxTimeReconnectKey = "max-time-to-reconnect"

  final val maxSubQueueSizeKey = "max-subscriber-queue-size"

  final val eventCacheSize = "event-cache-size"

  final val lastEventOnConnect = "last-event-on-connect"

  private val mapper = new ObjectMapper
  mapper.registerModule(new MapModule {})

  def apply(src: Array[Byte]) = mapper.readValue[Map[String, String]](src, classOf[Map[String,String]])

  implicit class RichStringMap(val props: Map[String, String]) extends AnyVal {

    /**
     * Obtains int property from the map, or returns a default value.
     * @param name The property name
     * @param default The default value
     * @param log Logger to allow logging in case of not an Int
     * @return The Int value or default value if it doesn't exist or can't be parsed
     */
    def getIntOrElse(name: String, default: Int)(implicit log: LoggingAdapter) =
      props.get(name) match {
        case Some(value) =>
          try {
            value.toInt
          } catch {
            case e: NumberFormatException =>
              log.warning(s"Non-integer value $value for property $name.")
              default
          }
        case None => default
      }

    /**
     * Obtains a boolean property from the map, or returns a default value.
     * @param name The property name
     * @param default The default value
     * @return The Boolean value or default value if the property does not exist
     */
    def getBooleanOrElse(name: String, default: Boolean) =
      props.get(name) match {
        case Some(value) => value.toBoolean
        case None => default
      }

    /**
     * Merge channel props on top of this props
     * @param channelProps The channelProps map
     * @return The merged map
     */
    def merge(channelProps: Map[String, String]): Map[String, String] = {
      val mergedProps = mutable.HashMap.empty[String, String]
      mergedProps ++= props ++= channelProps
      val chKeyVal = mergedProps.get(channelKey)
      val catKeyVal = mergedProps.get(catKey)
      // Remove the catKey if it exists
      if (catKeyVal != None) mergedProps -= catKey
      // Add the channelKey if it doesn't already exist, only if there is a catKey.
      if (chKeyVal == None) catKeyVal foreach (key => mergedProps += (channelKey -> key))
      mergedProps.toMap
    }

    def toJSONResponse = HttpResponse(OK, HttpEntity(`application/json`, mapper.writeValueAsBytes(props)))
  }
}

class CategoryBase extends Actor {

  import MapProps._

  def receive = {
    case n @ NewCategory(ctx, catId, props) =>
      context.child(catId) match {
        case Some(actor) =>
          ctx.responder ! HttpResponse(Conflict, s"Conflict: Category $catId already exists.")
        case None =>
          context.actorOf(Props(classOf[Category], props), catId)
          ctx.complete(s"OK: Category $catId created.")
      }

    case i: BaseInfo =>
      val infoMap = Map("Categories" -> context.children.size.toString)
      i.ctx.responder ! infoMap.toJSONResponse

    case l: ListCategories =>
      val requestUri = l.ctx.request.uri.toString()
      val thisPathLength = self.path.toString.length
      val categories = context.children map { actor =>
        val path = actor.path.toString
        val name = path substring (thisPathLength + 1)
        val url = requestUri + '/' + name
        name -> url
      }
      l.ctx.responder ! categories.toMap.toJSONResponse
   }
}

class Category(props: Map[String, String]) extends Actor {

  import MapProps._

  def receive = {
    case n @ NewChannel(ctx, catId, channelId, cProps) =>
      context.child(channelId) match {
        case Some(actor) =>
          ctx.responder ! HttpResponse(Conflict, s"Conflict: Channel $catId/$channelId already exists.")
        case None =>
          if (cProps.get(catKey) != props.get(catKey))
            ctx.responder ! HttpResponse(Unauthorized,
              s"Unauthorized: Channel creation in category $catId not authorized.")
          else {
            context.actorOf(Props(classOf[Channel], props merge cProps), channelId)
            ctx.complete(s"OK: Channel $catId/$channelId created.")
          }
      }

    case d: DeleteCategory =>
      if (d.props.get(catKey) != props.get(catKey))
        d.ctx.responder ! HttpResponse(Unauthorized, s"Unauthorized: Deletion of category ${d.catId} not authorized.")
      else if (!context.children.isEmpty)
        d.ctx.responder ! HttpResponse(Conflict, s"Conflict: Category ${d.catId} has active channels.")
      else {
        d.ctx.complete(s"OK: Category ${d.catId} successfully deleted.")
        context.stop(self)
      }

    case i: CategoryInfo =>
      val listMap = mutable.LinkedHashMap("Channels" -> context.children.size.toString)
      listMap ++= props -= catKey
      i.ctx.responder ! listMap.toMap.toJSONResponse

    case l: ListChannels =>
      val requestUri = l.ctx.request.uri.toString()
      val thisPathLength = self.path.toString.length
      val channels = context.children map { actor =>
        val path = actor.path.toString
        val name = path substring (thisPathLength + 1)
        val url = requestUri + '/' + name
        name -> url
      }
      l.ctx.responder ! channels.toMap.toJSONResponse
  }
}


class Channel(props: Map[String, String]) extends Actor with ActorLogging {

  import MapProps._

  implicit val logger = log

  val cache = EventCache(props.getIntOrElse(eventCacheSize, 1),
    props.getBooleanOrElse(lastEventOnConnect, default = true))

  val subscribers = mutable.HashSet.empty[ActorRef]

  def receive = {

    case p: Publish if p.keyOption == props.get(channelKey) =>
      val t = System.nanoTime
      val messageChunk = cache.storeAndFormat(p)
      subscribers foreach (_ ! messageChunk)
      val dt = System.nanoTime - t
      p.ctx.complete(s"OK: Published to ${subscribers.size} subscribers in $dt nanos.")

    case Publish(ctx, catId, channelId, _, _, _) =>
      ctx.responder ! HttpResponse(Unauthorized, s"Unauthorized: Publish to channel $catId/$channelId not authorized.")

    case sub: Subscribe =>
      val subscriber = context.actorOf(Props(classOf[Subscriber], sub.ctx, props))
      context watch subscriber
      subscribers += subscriber
      cache.eventsOnConnect(sub.evtIdOption) foreach (subscriber ! _)

    case Terminated(subscriber) =>
      subscribers -= subscriber

    case d: StopChannel if d.props.get(channelKey) == props.get(channelKey) =>
      val t = System.nanoTime
      subscribers foreach (_ ! ChunkedMessageEnd)
      val dt = System.nanoTime - t
      d.ctx.complete(s"OK: Channel ${d.actorPath} with ${subscribers.size} subscribers stopped in $dt nanos.")
      if (subscribers.size > 0) context.become(stopped, discardOld = true)
      else context.stop(self)

    case StopChannel(ctx, catId, channelId, _) =>
      ctx.responder ! HttpResponse(Unauthorized, s"Unauthorized: Stopping of channel $catId/$channelId not authorized.")

    case i: ChannelInfo =>
      val listMap = mutable.LinkedHashMap("Subscribers" -> subscribers.size.toString)
      listMap ++= props -= channelKey -= catKey
      i.ctx.responder ! listMap.toMap.toJSONResponse
  }

  def stopped: Receive = {

    case Terminated(subscriber) =>
      subscribers -= subscriber
      if (subscribers.size == 0) context.stop(self)

    case op: ChannelOps => op.ctx.responder ! HttpResponse(NotFound, "Not Found: Resource not found")
  }
}

class Subscriber(ctx: RequestContext, props: Map[String, String]) extends Actor with ActorLogging {

  case object ResponderReady
  case object Disconnect

  import MapProps._
  implicit val logger = log

  val maxTimeToReconnect = props.getIntOrElse(maxTimeReconnectKey, 120).seconds
  val maxSubscriberQueueSize = props.getIntOrElse(maxSubQueueSizeKey, 10)
  val evtQueue = mutable.Queue.empty[HttpResponsePart]

  import context.dispatcher
  val disconnectTask = context.system.scheduler.scheduleOnce(maxTimeToReconnect, self, Disconnect)

  def stop() {
    disconnectTask.cancel()
    context.stop(self)
  }

  def closeAndStop() {
    ctx.responder ! ChunkedMessageEnd
    stop()
  }

  ctx.responder ! ChunkedResponseStart(HttpResponse(headers=List(`Content-Type`(`text/event-stream`))))

  def mandatory: Receive = {
    // Connection closed sent from ctx.responder
    case ev: Http.ConnectionClosed =>
      log.warning("Connection closed, {}", ev)
      stop()

    case Disconnect =>
      log.info("Disconnecting subscriber " + ctx.request)
      ctx.responder ! ChunkedMessageEnd
      context.stop(self)
  }

  def receive = mandatory orElse {
    case c: MessageChunk =>
      ctx.responder ! (c withAck ResponderReady)
      context.become(channelQueueing, discardOld = false)

    case e: ChunkedMessageEnd =>
      ctx.responder ! MessageChunk(HttpData(SSESupport.streamEnd))
      closeAndStop()
  }

  def channelQueueing: Receive = mandatory orElse {
    case ResponderReady =>
      if (evtQueue.isEmpty) context.unbecome()
      else evtQueue.dequeue() match {
        case c: MessageChunk =>
          ctx.responder ! (c withAck ResponderReady)
        case e: ChunkedMessageEnd =>
          ctx.responder ! MessageChunk(HttpData(SSESupport.streamEnd))
          closeAndStop()
        case e =>
          log.warning(s"Unexpected response of type ${e.getClass.getName}")
      }

    case p: HttpResponsePart =>
      if (evtQueue.size < maxSubscriberQueueSize) evtQueue += p
      else {
        log.warning(s"Subscriber queue size grown to $maxSubscriberQueueSize, " +
          "client does not accept more events. Terminating.")
        closeAndStop()
      }
  }
}

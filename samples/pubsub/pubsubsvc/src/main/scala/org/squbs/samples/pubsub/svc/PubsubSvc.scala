/*
 * Copyright (c) 2013 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 */
package org.squbs.samples.pubsub.svc

import akka.actor._
import akka.util.ByteString
import spray.routing._
import Directives._
import spray.httpx.encoding.Gzip

import org.squbs.unicomplex.RouteDefinition

import org.squbs.samples.pubsub.messages._
import spray.http.StatusCodes._
import org.squbs.samples.pubsub.messages.NewChannel
import spray.http.HttpResponse
import org.squbs.samples.pubsub.messages.Subscribe
import org.squbs.samples.pubsub.messages.Publish
import org.squbs.samples.pubsub.messages.StopChannel
import org.squbs.samples.pubsub.messages.NewCategory
import org.squbs.samples.pubsub.messages.DeleteCategory
import org.squbs.samples.pubsub.messages.ChannelInfo
import org.squbs.samples.pubsub.messages.ListCategories
import org.squbs.samples.pubsub.messages.CategoryInfo
import akka.actor.ActorIdentity
import scala.Some
import akka.actor.Identify
import org.squbs.samples.pubsub.actors.MapProps

// this class defines our service behavior independently from the service actor
class PubSubSvc extends RouteDefinition {
  
  val router = context.actorOf(Props[RequestRouter], "RequestRouter")

  def route =
    get {
      path("evt" / Segment / Segment) { (catId, channelId) =>
        optionalHeaderValueByName("Last-Event-ID") { evtIdOption => ctx =>
          router ! Subscribe(ctx, catId, channelId, evtIdOption)
        }
      } ~
      path("adm" / "channels" / Segment) { catId => ctx =>
        router ! ListChannels(ctx, catId)
      } ~
      path("adm" / "categories") { ctx =>
        router ! ListCategories(ctx)
      } ~
      path("adm" / "info" / Segment / Segment) { (catId, channelId) => ctx =>
        router ! ChannelInfo(ctx, catId, channelId)
      } ~
      path("adm" / "info" / Segment) { catId => ctx =>
        router ! CategoryInfo(ctx, catId)
      } ~
      path("adm" / "info") { ctx =>
        router ! BaseInfo(ctx)
      }
    } ~
    post {
      path("pub" / Segment / Segment / Segment) { (catId, channelId, eventType) =>
        optionalHeaderValueByName(MapProps.channelKey) { keyOption =>
          entity(as[Array[Byte]]) { content => ctx =>
            router ! Publish(ctx, catId, channelId, eventType, keyOption, ByteString(content))
          }
        }
      } ~
      path("adm" / "stop" / Segment / Segment) { (catId, channelId) =>
        entity(as[Array[Byte]]) { props => ctx =>
          router ! StopChannel(ctx, catId, channelId, MapProps(props))
        }
      } ~
      path("adm" / "new" / Segment / Segment) { (catId, channelId) =>
        entity(as[Array[Byte]]) { props => ctx =>
          router ! NewChannel(ctx, catId, channelId, MapProps(props))
        }
      } ~
      path("adm" / "new" / Segment) { catId =>
        entity(as[Array[Byte]]) { props => ctx =>
          router ! NewCategory(ctx, catId, MapProps(props))
        }
      } ~
      path("adm" / "delete" / Segment) { catId =>
        entity(as[Array[Byte]]) { props => ctx =>
          router ! DeleteCategory(ctx, catId, MapProps(props))
        }
      }
    } ~
    path(""".*\.html""".r) { name =>
      encodeResponse(Gzip) {
        getFromResource("html/" + name)
      }
    } ~
    path("ping") {
      complete {
        <i>You have reached the pubsub service</i>
      }
    }
}

/**
 * The request router takes the request and tries to route it to the right handling actor.
 * If the handling actor does not exist, it responds to the HTTP request with a 404 - Not Found.
 * One singleton instance of this actor should be more than adequate.
 */
class RequestRouter extends Actor {

  def receive = {
    case obj: Ops =>
      context.actorSelection(obj.actorPath) ! Identify(obj)

    case ActorIdentity(obj, Some(actor)) =>
      actor ! obj

    case ActorIdentity(obj, None) =>
      obj.asInstanceOf[Ops].ctx.responder ! HttpResponse(NotFound, "Resource not found")
  }
}


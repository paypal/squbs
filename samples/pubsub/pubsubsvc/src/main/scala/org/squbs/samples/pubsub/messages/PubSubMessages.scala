/*
 * Copyright (c) 2014 eBay, Inc.
 * All rights reserved.
 *
 * Contributors:
 * asucharitakul
 */
package org.squbs.samples.pubsub.messages

import akka.util.ByteString
import spray.routing.RequestContext

trait Ops {
  val ctx: RequestContext
  protected val baseActorPath = "/user/pubsubsvc/cats"
  def actorPath = baseActorPath
}

trait CatOps extends Ops {
  val catId: String
  override def actorPath = s"$baseActorPath/$catId"
}

trait ChannelOps extends CatOps {
  val channelId: String
  override def actorPath = s"$baseActorPath/$catId/$channelId"
}

case class Publish(ctx: RequestContext, catId: String, channelId: String, eventType: String,
                   keyOption: Option[String], content: ByteString) extends ChannelOps

case class Subscribe(ctx: RequestContext, catId: String, channelId: String,
                     evtIdOption: Option[String]) extends ChannelOps

case class NewCategory(ctx: RequestContext, catId: String, props: Map[String, String]) extends Ops

case class DeleteCategory(ctx: RequestContext, catId: String, props: Map[String, String]) extends CatOps

case class NewChannel(ctx: RequestContext, catId: String, channelId: String,
                      props: Map[String, String]) extends CatOps

case class StopChannel(ctx: RequestContext, catId: String, channelId: String,
                         props: Map[String, String]) extends ChannelOps

case class ChannelInfo(ctx: RequestContext, catId: String, channelId: String) extends ChannelOps

case class CategoryInfo(ctx: RequestContext, catId: String) extends CatOps

case class BaseInfo(ctx: RequestContext) extends Ops

case class ListCategories(ctx: RequestContext) extends Ops

case class ListChannels(ctx: RequestContext, catId: String) extends CatOps

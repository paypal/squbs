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

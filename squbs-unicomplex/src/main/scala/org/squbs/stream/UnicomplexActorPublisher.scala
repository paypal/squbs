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
package org.squbs.stream

import java.lang.Boolean

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.actor.{ActorPublisher, ActorPublisherMessage}
import akka.stream.javadsl
import akka.stream.scaladsl.Source
import org.squbs.stream.TriggerEvent._
import org.squbs.unicomplex.{Active, Stopping, _}

final class UnicomplexActorPublisher extends ActorPublisher[LifecycleState] {

  override def receive = {
    case ActorPublisherMessage.Request(_) =>
      Unicomplex() ! SystemState
      Unicomplex() ! ObtainLifecycleEvents()
    case ActorPublisherMessage.Cancel | ActorPublisherMessage.SubscriptionTimeoutExceeded =>
      context.stop(self)
    case SystemState => Unicomplex() ! SystemState
    case element: LifecycleState if demand_? => onNext(element)
  }

  private def demand_? : Boolean = totalDemand > 0
}

case class LifecycleManaged[T, M]()(implicit system: ActorSystem) {
  val trigger = Source.actorPublisher[LifecycleState](Props.create(classOf[UnicomplexActorPublisher]))
    .collect {
      case Active => ENABLE
      case Stopping => DISABLE
    }

  val source = (in: Source[T, M]) => new Trigger(eagerComplete = true).source(in, trigger)

  // for Java
  def source(in: javadsl.Source[T, M]): javadsl.Source[T, akka.japi.Pair[M, ActorRef]] = source(in.asScala)
    .mapMaterializedValue {
      case (m1, m2) => akka.japi.Pair(m1, m2)
    }.asJava
}

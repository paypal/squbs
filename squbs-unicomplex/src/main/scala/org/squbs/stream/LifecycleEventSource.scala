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

import akka.actor.ActorRef
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.javadsl.{Source => JSource}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler, StageLogging}
import org.squbs.stream.TriggerEvent._
import org.squbs.unicomplex.{Active, Stopping, _}

final class LifecycleEventSource
  extends GraphStageWithMaterializedValue[SourceShape[LifecycleState], ActorRef] {

  val out: Outlet[LifecycleState] = Outlet("lifecycleEventSource.out")

  override def shape: SourceShape[LifecycleState] = SourceShape.of(out)

  trait LifecycleActorAccessible {
    def lifecycleActor: ActorRef
  }

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, ActorRef) = {
    val stage = new GraphStageLogic(shape) with StageLogging with LifecycleActorAccessible {

      getStageActor(_ => ()) // Initializes an empty stageActor
      override def lifecycleActor: ActorRef = stageActor.ref

      // Initialize the signals
      private val uniActor = Unicomplex.get(materializer.system).uniActor
      uniActor.tell(SystemState, stageActor.ref)
      uniActor.tell(ObtainLifecycleEvents(), stageActor.ref)

      // Set the `receive` for stageActor - it can't be set during initialization due to initialization dependencies.
      stageActor.become {
        case (_, state: LifecycleState) =>
          if (isAvailable(out)) push(out, state) else log.debug("Dropping state as there is no demand.")
        case (_, SystemState) => uniActor.tell(SystemState, stageActor.ref)
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = uniActor.tell(SystemState, stageActor.ref)
      })
    }

    (stage, stage.lifecycleActor)
  }
}

object LifecycleEventSource {
  /**
   * Creates a new Source for obtaining lifecycle events.
   * @return A new [[Source]] emitting [[LifecycleState]] events.
   */
  def apply(): Source[LifecycleState, ActorRef] = Source.fromGraph(new LifecycleEventSource)

  /**
   * Java API. Creates a new Source for obtaining lifecycle events.
   * @return A new [[JSource]] emitting [[LifecycleState]] events.
   */
  def create(): JSource[LifecycleState, ActorRef] = JSource.fromGraph(new LifecycleEventSource)
}

case class LifecycleManaged[T, M]() {
  val trigger: Source[TriggerEvent, ActorRef] = LifecycleEventSource()
    .collect {
      case Active => ENABLE
      case Stopping => DISABLE
    }

  val source: Source[T, M] => Source[T, (M, ActorRef)] =
    (in: Source[T, M]) => new Trigger(eagerComplete = true).source(in, trigger)

  // for Java
  def source(in: javadsl.Source[T, M]): javadsl.Source[T, akka.japi.Pair[M, ActorRef]] = source(in.asScala)
    .mapMaterializedValue {
      case (m1, m2) => akka.japi.Pair(m1, m2)
    }.asJava
}

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

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.javadsl.{Source => JSource}
import org.apache.pekko.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler, StageLogging}
import org.squbs.lifecycle.GracefulStop
import org.squbs.stream.TriggerEvent._
import org.squbs.unicomplex.{Active, Stopping, _}

import java.util.function.Supplier
import scala.compat.java8.FunctionConverters._

final class LifecycleEventSource
  extends GraphStageWithMaterializedValue[SourceShape[LifecycleState], () => ActorRef] {

  val out: Outlet[LifecycleState] = Outlet("lifecycleEventSource.out")

  override def shape: SourceShape[LifecycleState] = SourceShape.of(out)

  trait LifecycleActorAccessible {
    def lifecycleActor(): ActorRef
  }

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, () => ActorRef) = {
    val stage = new GraphStageLogic(shape) with StageLogging with LifecycleActorAccessible {

      private var uniActor: ActorRef = _

      override def lifecycleActor(): ActorRef = stageActor.ref

      override def preStart(): Unit = {
        uniActor = Unicomplex.get(materializer.system).uniActor

        getStageActor(_ => ()) // Initializes an empty stageActor
        // Set the `receive` for stageActor - it can't be set during initialization due to initialization dependencies.

        stageActor.become {
          case (_, state: LifecycleState) =>
            if (isAvailable(out)) push(out, state) else log.debug("Dropping state as there is no demand.")
          case (_, SystemState) => uniActor.tell(SystemState, stageActor.ref)
          case (_, GracefulStop) => complete(out)
        }

        // Initialize the signals
        uniActor.tell(SystemState, stageActor.ref)
        uniActor.tell(ObtainLifecycleEvents(), stageActor.ref)
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = uniActor.tell(SystemState, stageActor.ref)
      })
    }

    (stage, stage.lifecycleActor _)
  }
}

object LifecycleEventSource {
  /**
   * Creates a new Source for obtaining lifecycle events.
   * @return A new [[Source]] emitting [[LifecycleState]] events.
   */
  def apply(): Source[LifecycleState, () => ActorRef] = Source.fromGraph(new LifecycleEventSource)

  /**
   * Java API. Creates a new Source for obtaining lifecycle events.
   * @return A new [[JSource]] emitting [[LifecycleState]] events.
   */
  def create(): JSource[LifecycleState, Supplier[ActorRef]] =
    JSource.fromGraph(new LifecycleEventSource).mapMaterializedValue(_.asJava)
}

case class LifecycleManaged[T, M]() {
  val trigger: Source[TriggerEvent, () => ActorRef] = LifecycleEventSource()
    .collect {
      case Active => ENABLE
      case Stopping => DISABLE
    }

  val source: Source[T, M] => Source[T, (M, () => ActorRef)] =
    (in: Source[T, M]) => new Trigger(eagerComplete = true).source(in, trigger)

  // for Java
  def source(in: javadsl.Source[T, M]): javadsl.Source[T, org.apache.pekko.japi.Pair[M, Supplier[ActorRef]]] = source(in.asScala)
    .mapMaterializedValue {
      case (m1, m2) => org.apache.pekko.japi.Pair(m1, m2.asJava)
    }.asJava
}

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

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.{GraphDSL, Source}
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

/**
 * Merge two streams, taking elements as they arrive from an input stream, and a trigger stream to start/stop the flow
 * (pulling from trigger stream first, wait until true, then pulling from the input stream).
 * (keep pulling from trigger stream, wait until false, then stop pulling from the input stream).
 * (Repeat over)
 *
 * A `TriggerMerge` has one `out` port, one `in` port and one `trigger` input port.
 *
 * '''Emits when''' the input stream has an element available, and the triggered is true
 * Note that, trigger only impact the future input element(s), always allowing the in-flight element to go through
 */
final class TriggerMerge[T](eagerComplete: Boolean = false) extends GraphStage[FanInShape2[T, TriggerEvent, T]] {
  override val shape: FanInShape2[T, TriggerEvent, T] = new FanInShape2[T, TriggerEvent, T]("TriggerMerge")
  val in: Inlet[T] = shape.in0
  val trigger: Inlet[TriggerEvent] = shape.in1

  override def initialAttributes = Attributes.name("TriggerMerge")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    var flowEnabled, requestPending, triggerCompleted, willShutDown = false

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        push(out, grab(in))
        if (willShutDown) completeStage()
      }

      override def onUpstreamFinish(): Unit = {
        if (!isAvailable(in)) completeStage()
        willShutDown = true
      }
    })

    setHandler(trigger, new InHandler {
      override def onPush(): Unit = {
        val triggerEvent = grab(trigger)
        if (triggerEvent.value != flowEnabled) {
          if (triggerEvent.value && requestPending) {
            requestPending = false
            tryPull(in)
          }
          flowEnabled = triggerEvent.value
        }
        if (triggerCompleted) cancelTrigger()
        if (!hasBeenPulled(trigger)) tryPull(trigger)
      }

      override def onUpstreamFinish(): Unit = {
        if (!isAvailable(trigger)) cancelTrigger()
        triggerCompleted = true
      }

      def cancelTrigger(): Unit = if (eagerComplete) completeStage() else cancel(trigger)
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (willShutDown) completeStage()
        else {
          if (flowEnabled) tryPull(in)
          else {
            requestPending = true
            if (!hasBeenPulled(trigger)) tryPull(trigger)
          }
        }
      }
    })
  }

  def out: Outlet[T] = shape.out

  override def toString = "TriggerMerge"
}

class Trigger[T, M1, M2](eagerComplete: Boolean = false) {

  import GraphDSL.Implicits._

  private[stream] val source: (Graph[SourceShape[T], M1], Graph[SourceShape[TriggerEvent], M2]) => Source[T, (M1, M2)] =
    (in: Graph[SourceShape[T], M1], trigger: Graph[SourceShape[TriggerEvent], M2]) => Source.fromGraph(
      GraphDSL.createGraph(in, trigger)((_, _)) { implicit builder =>
        (sIn, sTrigger) =>
          val merge = builder.add(new TriggerMerge[T](eagerComplete))
          sIn ~> merge.in0
          sTrigger ~> merge.in1
          SourceShape(merge.out)
      })

  // for Java
  def source(in: javadsl.Source[T, M1], trigger: javadsl.Source[TriggerEvent, M2]):
      javadsl.Source[T, org.apache.pekko.japi.Pair[M1, M2]] = {
    source(in.asScala, trigger.asScala).mapMaterializedValue {
      case (m1, m2) => org.apache.pekko.japi.Pair(m1, m2)
    }.asJava
  }
}

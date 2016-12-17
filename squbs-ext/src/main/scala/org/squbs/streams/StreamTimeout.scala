/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.streams

import akka.stream.FlowShape
import akka.stream.scaladsl._

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Try}

object StreamTimeout {

  implicit class TimeoutFlowUnordered[Id, In, Out, Mat](flow: Flow[TimerEnvelope[Id, In], TimerEnvelope[Id, Out], Mat]) {

    /**
      *                                             +------------+
      *       +---------------+    +-----------+    |  Original  |    +---------+    +-------------+    +---------------+
      *       |               |    |           0 ~> 0    Flow    0 ~> 0         |    |             |    |               |
      *       |  Generate Id  |    |           |    +------------+    |         |    |             |    |   Map from    |
      * In ~> 0   and map to  0 ~> 0 Broadcast |                      |  Merge  0 ~> 0 Deduplicate 0 ~> 0 TimerEnvelope 0 ~> Try[Out]
      *       | TimerEnvelope |    |           |    +------------+    |         |    |             |    |   to Value    |
      *       |               |    |           0 ~> 0  Delay for 0 ~> 0         |    |             |    |               |
      *       +---------------+    +-----------+    |  timeout   |    +---------+    +-------------+    +---------------+
      *                                             +------------+
      *
      */
    def withTimeout(timeout: FiniteDuration, idGenerator: () => Id): Flow[In, Try[Out], Mat] = {

      Flow.fromGraph(GraphDSL.create(flow) { implicit b =>
        originalFlow =>
          import GraphDSL.Implicits._

          val converter = new TimerEnvelopeConverter(idGenerator)
          val toTimerEnvelope = b.add(Flow[In].map[TimerEnvelope[Id, In]](e => converter.toTimerEnvelope(e)))
          val merge = b.add(Merge[TimerEnvelope[Id, Try[Out]]](2))
          val broadcast = b.add(Broadcast[TimerEnvelope[Id, In]](2).async)

          val deDuplicate = b.add(Deduplicate[TimerEnvelope[Id, Try[Out]], Id]
            ((t: TimerEnvelope[Id, Try[Out]]) => t.id,
            2,
            new java.util.HashMap[Id, MutableLong]()))
          val fromTimerEnvelope = b.add(Flow[TimerEnvelope[Id, Try[Out]]].map(e => e.value))

          val mapToSuccess = Flow[TimerEnvelope[Id, Out]].map { case TimerEnvelope(id, element) =>
            TimerEnvelope[Id, Try[Out]](id, Try(element))
          }

          val mapToFailure = Flow[TimerEnvelope[Id, In]].map { case TimerEnvelope(id, _) =>
            TimerEnvelope[Id, Try[Out]](id, Failure(FlowTimeoutException()))
          }

          toTimerEnvelope ~> broadcast ~> originalFlow ~> mapToSuccess ~> merge ~> deDuplicate ~> fromTimerEnvelope
                             broadcast.delay(timeout)  ~> mapToFailure ~> merge

          FlowShape(toTimerEnvelope.in, fromTimerEnvelope.out)
      })
    }
  }

  implicit class TimeoutFlowUnorderedLong[In, Out, Mat]
  (flow: Flow[TimerEnvelope[Long, In], TimerEnvelope[Long, Out], Mat]) {

    def withTimeout(timeout: FiniteDuration): Flow[In, Try[Out], Mat] = {
      val flowWithLongId = new TimeoutFlowUnordered[Long, In, Out, Mat](flow)
      flowWithLongId.withTimeout(timeout, LongIdGenerator().nextId())
    }
  }

  implicit class TimeoutFlow[Id, In, Out, Mat](flow: Flow[In, Out, Mat]) {

    def withTimeout(timeout: FiniteDuration): Flow[In, Try[Out], Mat] = {
      flow.withTimeout(timeout, LongIdGenerator().nextId())
    }

    def withTimeout(timeout: FiniteDuration,
                    idGenerator: () => Id): Flow[In, Try[Out], Mat] = {

      /*
                                 +----------+                                  +----------+
                                 |          |                                  |          |
                                 |          0 ~> Id ~~~~~~~~~~~~~~~~~~~~~~~~~> 0          |
        TimerEnvelope[Id, In] ~> 0  Unzip   |          +-----------+           |   Zip    0 ~> TimerEnvelope[Id, Out]
                                 |          |          |           |           |          |
                                 |          0 ~> In ~> 0  Original 0 ~> Out ~> 0          |
                                 +----------+          |   Flow    |           +----------+
                                                       |           |
                                                       +-----------+

       */
      def timerEnvelopeAdapter[In, Out, Mat](flow: Flow[In, Out, Mat]):
      Flow[TimerEnvelope[Id, In], TimerEnvelope[Id, Out], Mat] = {
        Flow.fromGraph(GraphDSL.create(flow) { implicit b =>
          originalFlow =>
            import GraphDSL.Implicits._

            val unzip = b.add(UnzipWith[TimerEnvelope[Id, In], Id, In] { envelope => (envelope.id, envelope.value) })
            val zip = b.add(ZipWith[Id, Out, TimerEnvelope[Id, Out]] {
              case (id, value) => TimerEnvelope[Id, Out](id, value)
            })

            unzip.out0                 ~> zip.in0
            unzip.out1 ~> originalFlow ~> zip.in1

            FlowShape(unzip.in, zip.out)
        })
      }

      timerEnvelopeAdapter(flow).withTimeout(timeout, idGenerator)
    }
  }

  case class TimerEnvelope[Id, Value](val id: Id, val value: Value)

  class TimerEnvelopeConverter[Id](idGenerator: () => Id) {

    def toTimerEnvelope[Value](element: Value) = {
      TimerEnvelope[Id, Value](idGenerator(), element)
    }
  }

  case class FlowTimeoutException(msg: String = "Flow timed out!") extends Exception(msg)

  object LongIdGenerator {
    def apply() = new LongIdGenerator()
  }

  class LongIdGenerator {
    // This is not concurrent, nor needs it to be
    var id = 0L

    def nextId() = { () =>
      // It may overflow, which should be ok for most scenarios.  If the message with id 0 is still not completed
      // after it overflowed, then deduplication logic would be broken.  For such scenarios, a different
      // id generator should be used.
      id = id + 1
      id
    }
  }
}
/*
 * Copyright 2017 PayPal
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

package org.squbs

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.BidiShape
import org.apache.pekko.stream.scaladsl.{BidiFlow, Broadcast, GraphDSL, Merge}

package object pipeline {

  // (BidiFlow name, defaults on/off )
  type PipelineSetting = (Option[String], Option[Boolean])

  type PipelineFlow = BidiFlow[RequestContext, RequestContext, RequestContext, RequestContext, NotUsed]

  implicit class AbortableBidiFlow(val underlying: PipelineFlow) {

    def abortable: PipelineFlow = {
      underlying.atop(aborterFlow)
    }
  }

  val aborterFlow = BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val bCast = b.add(Broadcast[RequestContext](2))
    val out1 = bCast.out(0).filter(_.response.isEmpty).outlet

    val merge = b.add(Merge[RequestContext](2))
    bCast.out(1).filter(_.response.nonEmpty) ~> merge

    BidiShape(bCast.in, out1, merge.in(1), merge.out)
  })
}

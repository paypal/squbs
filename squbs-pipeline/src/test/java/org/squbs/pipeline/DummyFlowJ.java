/*
 *  Copyright 2018 PayPal
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

package org.squbs.pipeline;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.stream.BidiShape;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.javadsl.BidiFlow;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.GraphDSL;
import org.squbs.pipeline.Context;
import org.squbs.pipeline.RequestContext;
import org.squbs.pipeline.japi.PipelineFlowFactory;

//#create-pipelineflowfactory-java
public class DummyFlowJ extends PipelineFlowFactory {

    final private BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed> dummyBidi =
      BidiFlow.fromGraph(GraphDSL.create(b -> {
          final FlowShape<RequestContext, RequestContext> requestFlow = b.add(Flow.of(RequestContext.class)
            .map(rc -> rc.withRequestHeader(RawHeader.create("keyC", "valC"))));
          final FlowShape<RequestContext, RequestContext> responseFlow = b.add(Flow.of(RequestContext.class));
          return BidiShape.fromFlows(requestFlow, responseFlow);
      }));

    @Override
    public BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed>
    create(Context context, ActorSystem system) {

        return BidiFlow.fromGraph(GraphDSL.create(b -> {
            // each stage enriches RequestContext
            final FlowShape<RequestContext, RequestContext> stageA = b.add(Flow.of(RequestContext.class)
              .map(rc -> rc.withRequestHeader(RawHeader.create("keyA", "valA"))));
            final FlowShape<RequestContext, RequestContext> stageB = b.add(Flow.of(RequestContext.class)
              .map(rc -> rc.withRequestHeader(RawHeader.create("keyB", "valB"))));
            final BidiShape<RequestContext, RequestContext, RequestContext, RequestContext> stageC =
              b.add(dummyBidi);
            final FlowShape<RequestContext, RequestContext> stageD = b.add(Flow.of(RequestContext.class)
              .map(rc -> rc.withResponseHeader(RawHeader.create("keyD", "valD"))));

            b.from(stageA).via(stageB).toInlet(stageC.in1());
            b.to(stageD).fromOutlet(stageC.out2());

            return new BidiShape<>(stageA.in(), stageC.out1(), stageC.in2(), stageD.out());
        }));
    }
}
//#create-pipelineflowfactory-java



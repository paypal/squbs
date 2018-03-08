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

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.headers.RawHeader;
import akka.stream.BidiShape;
import akka.stream.FlowShape;
import akka.stream.javadsl.BidiFlow;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import org.squbs.pipeline.Context;
import org.squbs.pipeline.RequestContext;
import org.squbs.pipeline.japi.PipelineFlowFactory;

public class PreFlowJ implements PipelineFlowFactory {
    @Override
    public BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed>
    create(Context context, ActorSystem system) {
        return BidiFlow.fromGraph(GraphDSL.create(b -> {
            final FlowShape<RequestContext, RequestContext> inbound = b.add(Flow.of(RequestContext.class)
              .map(rc -> rc.addRequestHeader(RawHeader.create("keyPreInbound", "valPreInbound"))));
            final FlowShape<RequestContext, RequestContext> outbound = b.add(Flow.of(RequestContext.class)
              .map(rc -> rc.addResponseHeader(RawHeader.create("keyPreOutbound", "valPreOutbound"))));
            return BidiShape.fromFlows(inbound, outbound);
        }));
    }
}

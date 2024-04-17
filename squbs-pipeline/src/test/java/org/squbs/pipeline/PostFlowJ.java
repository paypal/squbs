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
import org.squbs.pipeline.japi.PipelineFlowFactory;

public class PostFlowJ extends PipelineFlowFactory {
    @Override
    public BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed>
    create(Context context, ActorSystem system) {
        return BidiFlow.fromGraph(GraphDSL.create(b -> {
            final FlowShape<RequestContext, RequestContext> inbound = b.add(Flow.of(RequestContext.class)
              .map(rc -> rc.withRequestHeader(RawHeader.create("keyPostInbound", "valPostInbound"))));
            final FlowShape<RequestContext, RequestContext> outbound = b.add(Flow.of(RequestContext.class)
              .map(rc -> rc.withResponseHeader(RawHeader.create("keyPostOutbound", "valPostOutbound"))));
            return BidiShape.fromFlows(inbound, outbound);
        }));
    }
}


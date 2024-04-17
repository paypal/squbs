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
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.stream.BidiShape;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.javadsl.BidiFlow;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.GraphDSL;
import org.squbs.pipeline.Context;
import org.squbs.pipeline.RequestContext;
import org.squbs.pipeline.japi.PipelineFlowFactory;

import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

//#create-abortable-pipelineflowfactory-java
public class DummyFlow2J extends PipelineFlowFactory {

    final private Flow<RequestContext, RequestContext, NotUsed> dummyAborterFlow = Flow.<RequestContext>create().map(rc ->
        rc.getRequest().getHeader("abort").map(x -> rc.abortWith(
            HttpResponse.create()
                .withEntity(StreamSupport.stream(rc.getRequest().getHeaders().spliterator(), false)
                    .sorted(Comparator.comparing(HttpHeader::name))
                    .map(Object::toString)
                    .collect(Collectors.joining(",")))))
            .orElse(rc));


    final private BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed> dummyBidi =
        BidiFlow.fromGraph(GraphDSL.create(b -> {
            final FlowShape<RequestContext, RequestContext> requestFlow = b.add(Flow.of(RequestContext.class)
                .map(rc -> rc.withRequestHeader(RawHeader.create("keyC1", "valC1"))).via(dummyAborterFlow));
            final FlowShape<RequestContext, RequestContext> responseFlow = b.add(Flow.of(RequestContext.class)
                .map(rc -> rc.withResponseHeader(RawHeader.create("keyC2", "valC2"))));
            return BidiShape.fromFlows(requestFlow, responseFlow);
        }));

    @Override
    public BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed>
    create(Context context, ActorSystem system) {

        return BidiFlow.fromGraph(GraphDSL.create(b -> {
            final FlowShape<RequestContext, RequestContext> stageA = b.add(Flow.of(RequestContext.class)
                .map(rc -> rc.withRequestHeader(RawHeader.create("keyA", "valA"))));
            final FlowShape<RequestContext, RequestContext> stageB = b.add(Flow.of(RequestContext.class)
                .map(rc -> rc.withRequestHeader(RawHeader.create("keyB", "valB"))));
            final BidiShape<RequestContext, RequestContext, RequestContext, RequestContext> stageC =
                b.add(abortable(dummyBidi));
            final FlowShape<RequestContext, RequestContext> stageD = b.add(Flow.of(RequestContext.class)
                .map(rc -> rc.withRequestHeader(RawHeader.create("keyD", "valD"))));
            final FlowShape<RequestContext, RequestContext> stageE = b.add(Flow.of(RequestContext.class)
                .map(rc -> rc.withResponseHeader(RawHeader.create("keyE", "valE"))));
            final FlowShape<RequestContext, RequestContext> stageF = b.add(Flow.of(RequestContext.class)
                .map(rc -> rc.withResponseHeader(RawHeader.create("keyF", "valF"))));

            b.from(stageA).via(stageB).toInlet(stageC.in1());
            b.to(stageD).fromOutlet(stageC.out1());
            b.from(stageE).toInlet(stageC.in2());
            b.to(stageF).fromOutlet(stageC.out2());

            return new BidiShape<>(stageA.in(), stageD.out(), stageE.in(), stageF.out());
        }));
    }

}
//#create-abortable-pipelineflowfactory-java
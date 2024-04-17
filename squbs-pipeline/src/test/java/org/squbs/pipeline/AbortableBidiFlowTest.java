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

/*
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.stream.javadsl.*;
import org.apache.pekko.util.ByteString;
import com.typesafe.config.ConfigFactory;
import org.scalatest.Ignore;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import scala.Option;
import scala.Tuple2;
import scala.util.Success;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.testng.Assert.assertEquals;
@Ignore
public class AbortableBidiFlowTest {

    private static final String cfg =
        "dummyFlow2 {\n" +
        "  type = squbs.pipelineflow\n" +
        "  factory = org.squbs.pipeline.DummyFlow2J\n" +
        "}\n" +
        "preFlow {\n" +
        "  type = squbs.pipelineflow\n" +
        "  factory = org.squbs.pipeline.PreFlowJ\n" +
        "}\n" +
        "postFlow {\n" +
        "  type = squbs.pipelineflow\n" +
        "  factory = org.squbs.pipeline.PostFlowJ\n" +
        "}\n" +
        "squbs.pipeline.server.default {\n" +
        "  pre-flow = preFlow\n" +
        "  post-flow = postFlow\n" +
        "}\n";

    private static final ActorSystem system = ActorSystem.create("AbortableBidiFlowTest",
        ConfigFactory.parseString(cfg));
    private static final PipelineExtensionImpl pipeLineExtension = PipelineExtension.get(system);

    private final Flow<RequestContext, RequestContext, NotUsed> dummyEndpoint = Flow.<RequestContext>create().map(rc ->
        RequestContext.create(rc.getRequest(), rc.httpPipeliningOrder())
            .withResponse(Success.apply(HttpResponse.create()
                            .withEntity(StreamSupport.stream(rc.getRequest().getHeaders().spliterator(), false)
                                    .sorted(Comparator.comparing(HttpHeader::name))
                                    .map(Object::toString)
                                    .collect(Collectors.joining(","))))));

    @Test
    public void testAbortableFlow() throws Exception {
        final BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed> pipelineFlow =
            pipeLineExtension.getFlow(new Tuple2<>(Option.apply("dummyFlow2"), Option.apply(true)),
                new Context("dummy", ServerPipeline$.MODULE$)).get().asJava();

        Flow<RequestContext, RequestContext, NotUsed> httpFlow = pipelineFlow.join(dummyEndpoint);
        final CompletionStage<RequestContext> result = Source
            .single(RequestContext.create(HttpRequest.create(), 0L))
            .runWith(httpFlow.toMat(Sink.head(), Keep.right()), system);

        final HttpResponse httpResponse = result.toCompletableFuture()
            .get(Timeouts.awaitMax().toMillis(), TimeUnit.MILLISECONDS).getResponse().get().get();

        final List<HttpHeader> respHeaders = new ArrayList<>();
        httpResponse.getHeaders().forEach(respHeaders::add);
        respHeaders.sort(Comparator.comparing(HttpHeader::name));

        List<RawHeader> expectedHeaders = Arrays.asList(
            RawHeader.create("keyE", "valE"),
            RawHeader.create("keyC2", "valC2"),
            RawHeader.create("keyF", "valF"),
            RawHeader.create("keyPreOutbound", "valPreOutbound"),
            RawHeader.create("keyPostOutbound", "valPostOutbound")
        );
        expectedHeaders.sort(Comparator.comparing(HttpHeader::name));
        assertEquals(respHeaders, expectedHeaders);

        final String actualEntity = httpResponse.entity().getDataBytes()
            .runFold(ByteString.emptyByteString(), ByteString::concat, system).toCompletableFuture().get().utf8String();
        final RawHeader[] entityList = {
            RawHeader.create("keyA", "valA"),
            RawHeader.create("keyB", "valB"),
            RawHeader.create("keyC1", "valC1"),
            RawHeader.create("keyD", "valD"),
            RawHeader.create("keyPreInbound", "valPreInbound"),
            RawHeader.create("keyPostInbound", "valPostInbound")
        };
        String expectedEntity = Arrays.stream(entityList)
            .sorted(Comparator.comparing(HttpHeader::name))
            .map(Object::toString)
            .collect(Collectors.joining(","));
        assertEquals(actualEntity, expectedEntity);
    }

    @Test
    public void testAbortableFlowWithAbort() throws Exception {
        final BidiFlow<RequestContext, RequestContext, RequestContext, RequestContext, NotUsed> pipelineFlow =
            pipeLineExtension.getFlow(new Tuple2<>(Option.apply("dummyFlow2"), Option.apply(true)),
                new Context("dummy", ServerPipeline$.MODULE$)).get().asJava();

        Flow<RequestContext, RequestContext, NotUsed> httpFlow = pipelineFlow.join(dummyEndpoint);
        RawHeader abortHeader = RawHeader.create("abort", "dummyValue");
        final CompletionStage<RequestContext> result = Source
            .single(RequestContext.create(HttpRequest.create().addHeader(abortHeader), 0L))
            .runWith(httpFlow.toMat(Sink.head(), Keep.right()), system);

        final HttpResponse httpResponse = result.toCompletableFuture()
            .get(Timeouts.awaitMax().toMillis(), TimeUnit.MILLISECONDS).getResponse().get().get();

        final List<HttpHeader> respHeaders = new ArrayList<>();
        httpResponse.getHeaders().forEach(respHeaders::add);
        respHeaders.sort(Comparator.comparing(HttpHeader::name));

        List<RawHeader> expectedHeaders = Arrays.asList(
            RawHeader.create("keyC2", "valC2"),
            RawHeader.create("keyF", "valF"),
            RawHeader.create("keyPreOutbound", "valPreOutbound")
        );
        expectedHeaders.sort(Comparator.comparing(HttpHeader::name));
        assertEquals(respHeaders, expectedHeaders);

        final String actualEntity = httpResponse.entity().getDataBytes()
            .runFold(ByteString.emptyByteString(), ByteString::concat, system).toCompletableFuture().get().utf8String();
        final RawHeader[] entityList = {
            RawHeader.create("abort", "dummyValue"),
            RawHeader.create("keyA", "valA"),
            RawHeader.create("keyB", "valB"),
            RawHeader.create("keyC1", "valC1"),
            RawHeader.create("keyPreInbound", "valPreInbound")
        };
        String expectedEntity = Arrays.stream(entityList)
            .sorted(Comparator.comparing(HttpHeader::name))
            .map(Object::toString)
            .collect(Collectors.joining(","));
        assertEquals(actualEntity, expectedEntity);
    }

    @AfterClass
    public static void tearDown() {
        system.terminate();
    }

}*/
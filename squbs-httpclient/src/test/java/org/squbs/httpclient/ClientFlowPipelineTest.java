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
package org.squbs.httpclient;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.ConnectHttp;
import org.apache.pekko.http.javadsl.HostConnectionPool;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.Test;
import org.squbs.resolver.ResolverRegistry;
import org.squbs.testkit.Timeouts;
import scala.util.Try;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ClientFlowPipelineTest {

    private static final String cfg =
            "dummyFlow {\n" +
            "  type = squbs.pipelineflow\n" +
            "  factory = org.squbs.httpclient.DummyFlow\n" +
            "}\n" +
            "preFlow {\n" +
            "  type = squbs.pipelineflow\n" +
            "  factory = org.squbs.httpclient.PreFlow\n" +
            "}\n" +
            "postFlow {\n" +
            "  type = squbs.pipelineflow\n" +
            "  factory = org.squbs.httpclient.PostFlow\n" +
            "}\n" +
            "squbs.pipeline.client.default {\n" +
            "  pre-flow =  preFlow\n" +
            "  post-flow = postFlow\n" +
            "}\n" +
            "clientWithCustomPipelineWithDefaults {\n" +
            "  type = squbs.httpclient\n" +
            "  pipeline = dummyFlow\n" +
            "}\n" +
            "clientWithOnlyDefaults {\n" +
            "  type = squbs.httpclient\n" +
            "}\n" +
            "clientWithCustomPipelineWithoutDefaults {\n" +
            "  type = squbs.httpclient\n" +
            "  pipeline = dummyFlow\n" +
            "  defaultPipeline = off\n" +
            "}\n" +
            "clientWithNoPipeline {\n" +
            "  type = squbs.httpclient\n" +
            "  defaultPipeline = off\n" +
            "}\n";

    private static final ActorSystem system = ActorSystem.create("ClientFlowPipelineSpec",
            ConfigFactory.parseString(cfg));
    private static final Materializer mat = ActorMaterializer.create(system);
    private static final Flow<HttpRequest, HttpResponse, NotUsed> flow = new MyRoute().route().flow(system, mat);

    private static final ServerBinding serverBinding;

    static {
        ServerBinding binding;
        try {
            binding = Http.get(system).
                    bindAndHandle(flow, ConnectHttp.toHost("localhost", 0), mat).
                    toCompletableFuture().get();
        } catch(Exception e) {
            binding = null;
        }
        serverBinding = binding;
    }

    private static final int port = serverBinding.localAddress().getPort();

    static {
        ResolverRegistry.get(system).register(HttpEndpoint.class, "LocalhostEndpointResolver", (svcName, env) ->
                Optional.of(HttpEndpoint.create("http://localhost:" + port)));
    }

    static class MyRoute extends AllDirectives {
        Route route() {
            return path("hello", () ->
                    extract(rc -> rc.getRequest().getHeaders(), headers ->
                            complete(
                                    StreamSupport
                                            .stream(headers.spliterator(), false)
                                            .filter(h -> h.name().startsWith("key"))
                                            .sorted(Comparator.comparing(HttpHeader::name))
                                            .map(Object::toString)
                                            .collect(Collectors.joining(","))
                            )
                    )
            );
        }
    }

    @AfterClass
    public static void afterAll() {
        serverBinding.unbind().thenAccept(u -> system.terminate());
    }

    @Test
    public void buildFlowWithDefaults() throws ExecutionException, InterruptedException {
        RawHeader[] expectedResponseHeaders = {
                RawHeader.create("keyD", "valD"),
                RawHeader.create("keyPostOutbound", "valPostOutbound"),
                RawHeader.create("keyPreOutbound", "valPreOutbound")
        };

        RawHeader[] entityList = {
                RawHeader.create("keyA", "valA"),
                RawHeader.create("keyB", "valB"),
                RawHeader.create("keyC", "valC"),
                RawHeader.create("keyPreInbound", "valPreInbound"),
                RawHeader.create("keyPostInbound", "valPostInbound")
        };
        String expectedEntity = Arrays.stream(entityList)
                .sorted(Comparator.comparing(HttpHeader::name))
                .map(Object::toString)
                .collect(Collectors.joining(","));
        assertPipeline("clientWithCustomPipelineWithDefaults", expectedResponseHeaders, expectedEntity);
    }

    @Test
    public void buildFlowWithOnlyDefaults() throws ExecutionException, InterruptedException {
        RawHeader[] expectedResponseHeaders = {
                RawHeader.create("keyPostOutbound", "valPostOutbound"),
                RawHeader.create("keyPreOutbound", "valPreOutbound")
        };

        RawHeader[] entityList = {
                RawHeader.create("keyPreInbound", "valPreInbound"),
                RawHeader.create("keyPostInbound", "valPostInbound")
        };

        String expectedEntity = Arrays.stream(entityList)
                .sorted(Comparator.comparing(HttpHeader::name))
                .map(Object::toString)
                .collect(Collectors.joining(","));

        assertPipeline("clientWithOnlyDefaults", expectedResponseHeaders, expectedEntity);
    }

    @Test
    public void buildFlowWithoutDefaults() throws ExecutionException, InterruptedException {
        RawHeader[] expectedResponseHeaders = { RawHeader.create("keyD", "valD") };

        RawHeader[] entityList = {
                RawHeader.create("keyA", "valA"),
                RawHeader.create("keyB", "valB"),
                RawHeader.create("keyC", "valC")
        };

        String expectedEntity = Arrays.stream(entityList)
                .sorted(Comparator.comparing(HttpHeader::name))
                .map(Object::toString)
                .collect(Collectors.joining(","));

        assertPipeline("clientWithCustomPipelineWithoutDefaults", expectedResponseHeaders, expectedEntity);
    }

    @Test
    public void withoutPipeline() throws ExecutionException, InterruptedException {
        assertPipeline("clientWithNoPipeline", new RawHeader[0], "");
    }

    private void assertPipeline(String clientName, RawHeader[] expectedResponseHeaders, String expectedEntity)
            throws ExecutionException, InterruptedException {
        final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow =
                ClientFlow.create(clientName, system, mat);
        final HttpResponse response = Source
                .single(Pair.create(HttpRequest.create("/hello"), 42))
                .via(clientFlow)
                .runWith(Sink.head(), mat)
                .thenApply(Pair::first)
                .toCompletableFuture()
                .get()
                .get();
        assertEquals(StatusCodes.OK, response.status());
        final HttpHeader[] headers = StreamSupport
                .stream(response.getHeaders().spliterator(), false)
                .filter(h -> h.name().startsWith("key"))
                .sorted(Comparator.comparing(HttpHeader::name))
                .toArray(HttpHeader[]::new);

        assertArrayEquals(expectedResponseHeaders, headers);
        final HttpEntity.Strict entity = response.entity().toStrict(Timeouts.awaitMax().toMillis(), mat)
                .toCompletableFuture().get();
        assertEquals(expectedEntity, entity.getData().utf8String());
    }
}

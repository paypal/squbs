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
package org.squbs.httpclient;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.HostConnectionPool;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.junit.AfterClass;
import org.junit.Test;
import org.squbs.endpoint.Endpoint;
import org.squbs.endpoint.EndpointResolverRegistry;
import org.squbs.testkit.Timeouts;
import scala.util.Try;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;

public class ClientFlowTest {

    private static final ActorSystem system = ActorSystem.create("ClientFlowTest");
    private static final ActorMaterializer mat = ActorMaterializer.create(system);
    private static final Flow<HttpRequest, HttpResponse, NotUsed> flow = new MyRoute().route().flow(system, mat);

    private static final ServerBinding serverBinding;

    static {
        ServerBinding binding;
        try {
            binding = Http.get(system).
                    bindAndHandle(flow, ConnectHttp.toHost("localhost", 65525), mat).
                    toCompletableFuture().get();
        } catch(Exception e) {
            binding = null;
        }
        serverBinding = binding;
    }
    private static final int port = serverBinding.localAddress().getPort();

    private final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow;

    static {
        EndpointResolverRegistry.get(system).register("LocalhostEndpointResolver", (svcName, env) -> {
            if ("hello".equals(svcName)) {
                return Optional.of(Endpoint.create("http://localhost:" + port));
            }
            return Optional.empty();
        });
    }

    public ClientFlowTest() {
        clientFlow = ClientFlow.create("hello", system, mat);
    }

    CompletionStage<Try<HttpResponse>> doRequest(HttpRequest request) {
        return Source
                .single(Pair.create(request, 42))
                .via(clientFlow)
                .runWith(Sink.head(), mat)
                .thenApply(Pair::first);
    }


    @AfterClass
    public static void afterAll() {
        serverBinding.unbind().thenAccept(u -> system.terminate());
    }

    @Test
    public void testClientCallHelloService() throws Exception {
        CompletionStage<Try<HttpResponse>> cs = doRequest(HttpRequest.create("/hello"));
        HttpResponse response = cs.toCompletableFuture().get().get();
        assertEquals(StatusCodes.OK, response.status());
        String content = response.entity().toStrict(Timeouts.awaitMax().toMillis(), mat)
                .toCompletableFuture().get().getData().utf8String();
        assertEquals("Hello World!", content);
    }

    @Test(expected = HttpClientEndpointNotExistException.class)
    public void endPointNotExist() throws Exception {
        ClientFlow.<Integer>create("cannotResolve", system, mat);
    }
}

class MyRoute extends AllDirectives {

    Route route() {
        return path("hello", () ->
                get(() ->
                        complete(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, "Hello World!"))
                )
        );

    }
}
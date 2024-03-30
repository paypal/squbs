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

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.ConnectHttp;
import org.apache.pekko.http.javadsl.HostConnectionPool;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.DateTime;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.ProductVersion;
import org.apache.pekko.http.javadsl.model.headers.Server;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.squbs.resolver.ResolverRegistry;
import org.squbs.streams.circuitbreaker.CircuitBreakerOpenException;
import org.squbs.streams.circuitbreaker.CircuitBreakerState;
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState;
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class ClientFlowCircuitBreakerTest {

    private static final Config config = ConfigFactory.parseString(
            "disableCircuitBreaker { \n" +
                "  type = squbs.httpclient\n" +
                "}");

    private static final ActorSystem system = ActorSystem.create("ClientFlowCircuitBreakerTest", config);
    private static final Materializer mat = Materializer.createMaterializer(system);
    private static final Route route =
            new MyProblematicRoute().route();

    private static final HttpResponse INTERNAL_SERVER_ERROR_RESPONSE =
            HttpResponse.create()
                    .withStatus(StatusCodes.INTERNAL_SERVER_ERROR)
                    .addHeader(Server.create(ProductVersion.create("testServer", "1.0")))
                    .addHeader(org.apache.pekko.http.javadsl.model.headers.Date.create(DateTime.create(2017, 1, 1, 1, 1, 1)));

    private static final ServerBinding serverBinding;

    static {
        ServerBinding binding;
        try {
            binding = Http.get(system)
                    .newServerAt("localhost", 0)
                    .bind(route)
                    .toCompletableFuture()
                    .get();
        } catch(Exception e) {
            binding = null;
        }
        serverBinding = binding;
    }

    private static final int port = serverBinding.localAddress().getPort();

    int defaultMaxFailures = system.settings().config().getInt("squbs.circuit-breaker.max-failures");
    int defaultMaxConnections = system.settings().config().getInt("pekko.http.host-connection-pool.max-connections");
    int numOfRequests = (defaultMaxFailures + defaultMaxConnections) * 2; // Some random large number
    int numOfPassThroughBeforeCircuitBreakerIsOpen = defaultMaxConnections + defaultMaxFailures - 1;
    int numOfFailFast = numOfRequests - numOfPassThroughBeforeCircuitBreakerIsOpen;

    static {
        ResolverRegistry.get(system).register(HttpEndpoint.class, "LocalhostEndpointResolver",
                (svcName, env) -> Optional.of(HttpEndpoint.create("http://localhost:" + port)));
    }


    @AfterClass
    public static void afterAll() {
        serverBinding.unbind().thenAccept(u -> system.terminate());
    }

    @Test
    public void testFailFastUsingDefaultFailureDecider() throws ExecutionException, InterruptedException {

        CircuitBreakerState circuitBreakerState = AtomicCircuitBreakerState
                .create("internalServerError", system.settings().config().getConfig("squbs.circuit-breaker"), system);
        CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.<HttpRequest, HttpResponse, Integer>create(circuitBreakerState);

        final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow =
                ClientFlow.builder()
                        .withCircuitBreakerSettings(circuitBreakerSettings)
                        .create("http://localhost:" + port, system, mat);


        CompletionStage<List<Try<HttpResponse>>> responseSeq =
                Source.range(1, numOfRequests)
                        .map(i -> Pair.create(HttpRequest.create("/internalServerError"), i))
                        .via(clientFlow)
                        .map(pair -> pair.first())
                        .runWith(Sink.seq(), mat);

        List<Try<HttpResponse>> actual = responseSeq.toCompletableFuture().get();
        Assert.assertEquals(numOfPassThroughBeforeCircuitBreakerIsOpen,
                Collections.frequency(actual, Success.apply(INTERNAL_SERVER_ERROR_RESPONSE)));

        Assert.assertEquals(numOfFailFast,
                Collections.frequency(actual,
                        Failure.apply(new CircuitBreakerOpenException("Circuit Breaker is open; calls are failing fast!"))));
    }

    @Test
    public void testDisableCircuitBreaker() throws ExecutionException, InterruptedException {

        CompletionStage<List<Try<HttpResponse>>> responseSeq =
                Source.range(1, numOfRequests)
                        .map(i -> Pair.create(HttpRequest.create("/internalServerError"), i))
                        .via(ClientFlow.create("disableCircuitBreaker", system, mat))
                        .map(pair -> pair.first())
                        .runWith(Sink.seq(), mat);

        List<Try<HttpResponse>> actual = responseSeq.toCompletableFuture().get();
        Assert.assertEquals(numOfRequests,
                Collections.frequency(actual, Success.apply(INTERNAL_SERVER_ERROR_RESPONSE)));
    }

    @Test
    public void testFallback() throws ExecutionException, InterruptedException {

        final CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.<HttpRequest, HttpResponse, Integer>create(
                        AtomicCircuitBreakerState.create("javaFallbackClient", ConfigFactory.empty(), system))
                        .withFallback((HttpRequest httpRequest) ->
                                Success.apply(HttpResponse.create()
                                        .withEntity("Fallback Response")));

        final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow =
                ClientFlow.builder()
                        .withCircuitBreakerSettings(circuitBreakerSettings)
                        .create("javaFallbackClient", system, mat);

        CompletionStage<List<Try<HttpResponse>>> responseSeq =
                Source.range(1, numOfRequests)
                        .map(i -> Pair.create(HttpRequest.create("/internalServerError"), i))
                        .via(clientFlow)
                        .map(pair -> pair.first())
                        .runWith(Sink.seq(), mat);

        List<Try<HttpResponse>> actual = responseSeq.toCompletableFuture().get();
        Assert.assertEquals(numOfPassThroughBeforeCircuitBreakerIsOpen,
                Collections.frequency(actual, Success.apply(INTERNAL_SERVER_ERROR_RESPONSE)));

        Assert.assertEquals(numOfFailFast,
                Collections.frequency(actual,
                        Success.apply(HttpResponse.create().withEntity("Fallback Response"))));
    }

    @Test
    public void testCustomFailureDecider() throws ExecutionException, InterruptedException {

        final CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.<HttpRequest, HttpResponse, Integer>create(
                        AtomicCircuitBreakerState.create("javaCustomFailureDeciderClient", ConfigFactory.empty(), system))
                        .withFailureDecider(httpResponseTry -> false);

        final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow =
                ClientFlow.builder()
                        .withCircuitBreakerSettings(circuitBreakerSettings)
                        .create("javaCustomFailureDeciderClient", system, mat);

        CompletionStage<List<Try<HttpResponse>>> responseSeq =
                Source.range(1, numOfRequests)
                        .map(i -> Pair.create(HttpRequest.create("/internalServerError"), i))
                        .via(clientFlow)
                        .map(pair -> pair.first())
                        .runWith(Sink.seq(), mat);

        List<Try<HttpResponse>> actual = responseSeq.toCompletableFuture().get();
        Assert.assertEquals(numOfRequests,
                Collections.frequency(actual, Success.apply(INTERNAL_SERVER_ERROR_RESPONSE)));
    }

    private static class MyProblematicRoute extends AllDirectives {

        Route route() {
            return path("internalServerError", () ->
                    complete(INTERNAL_SERVER_ERROR_RESPONSE)
            );
        }
    }
}

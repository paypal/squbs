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
import org.apache.pekko.http.javadsl.*;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.settings.ConnectionPoolSettings;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.AfterClass;
import org.junit.Test;
import org.squbs.env.PROD;
import org.squbs.resolver.ResolverRegistry;
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState;
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;
import org.squbs.testkit.Timeouts;
import scala.util.Try;

import javax.management.ObjectName;
import javax.net.ssl.SSLContext;
import java.lang.management.ManagementFactory;
import java.time.Duration;
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
                    bindAndHandle(flow, ConnectHttp.toHost("localhost", 0), mat).
                    toCompletableFuture().get();
        } catch(Exception e) {
            binding = null;
        }
        serverBinding = binding;
    }
    private static final int port = serverBinding.localAddress().getPort();

    private final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow;

    static {
        ResolverRegistry.get(system).register(HttpEndpoint.class, "LocalhostEndpointResolver", (svcName, env) -> {
            if (svcName.matches("hello|javaBuilderClient|javaAllInputsClient")) {
                return Optional.of(HttpEndpoint.create("http://localhost:" + port));
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

    @Test
    public void testBuilderAPI() throws Exception {
        ConnectionPoolSettings connectionPoolSettings = ConnectionPoolSettings.create(system).withMaxConnections(41);
        CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.create(
                        AtomicCircuitBreakerState.create(
                                "javaBuilderClient",
                                11,
                                Duration.ofSeconds(12),
                                Duration.ofMinutes(13),
                                Duration.ofDays(14),
                                16.0,
                                system.dispatcher(),
                                system.scheduler()));

        ClientFlow.builder()
                .withConnectionContext(ConnectionContext.https(SSLContext.getInstance("TLS")))
                .withSettings(connectionPoolSettings)
                .withCircuitBreakerSettings(circuitBreakerSettings)
                .withEnvironment(PROD.value())
                .create("javaBuilderClient", system, mat);

        ObjectName oNameHC = ObjectName.getInstance(
                "org.squbs.configuration." + system.name() + ":type=squbs.httpclient,name=" +
                        ObjectName.quote("javaBuilderClient"));

        ObjectName oNameCB = ObjectName.getInstance(
                "org.squbs.configuration:type=squbs.circuitbreaker,name=" + ObjectName.quote("javaBuilderClient"));

        assertJmxValue(oNameHC, "MaxConnections", 41);
        assertJmxValue(oNameHC, "Environment", "PROD");
        assertJmxValue(oNameCB, "MaxFailures", 11);
        assertJmxValue(oNameCB, "CallTimeout", "12 seconds");
        assertJmxValue(oNameCB, "ResetTimeout", "13 minutes");
        assertJmxValue(oNameCB, "MaxResetTimeout", "14 days");
        assertJmxValue(oNameCB, "ExponentialBackoffFactor", 16.0);
    }

    @Test
    public void testAllInputsAPI() throws Exception {
        ConnectionPoolSettings connectionPoolSettings = ConnectionPoolSettings.create(system).withMaxConnections(41);
        CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.create(
                        AtomicCircuitBreakerState.create(
                                "javaAllInputsClient",
                                11,
                                Duration.ofSeconds(12),
                                Duration.ofMinutes(13),
                                Duration.ofDays(14),
                                16.0,
                                system.dispatcher(),
                                system.scheduler()));

        ClientFlow.builder()
                .withConnectionContext(ConnectionContext.https(SSLContext.getInstance("TLS")))
                .withSettings(connectionPoolSettings)
                .withCircuitBreakerSettings(circuitBreakerSettings)
                .withEnvironment(PROD.value())
                .create("javaAllInputsClient", system, mat);

        ClientFlow.create(
                "javaAllInputsClient",
                Optional.of(ConnectionContext.https(SSLContext.getInstance("TLS"))),
                Optional.of(connectionPoolSettings),
                Optional.of(circuitBreakerSettings),
                PROD.value(),
                system,
                mat);

        ObjectName oNameHC = ObjectName.getInstance(
                "org.squbs.configuration." + system.name() + ":type=squbs.httpclient,name=" +
                        ObjectName.quote("javaAllInputsClient"));

        ObjectName oNameCB = ObjectName.getInstance(
                "org.squbs.configuration:type=squbs.circuitbreaker,name=" + ObjectName.quote("javaAllInputsClient"));

        assertJmxValue(oNameHC, "MaxConnections", 41);
        assertJmxValue(oNameHC, "Environment", "PROD");
        assertJmxValue(oNameCB, "MaxFailures", 11);
        assertJmxValue(oNameCB, "CallTimeout", "12 seconds");
        assertJmxValue(oNameCB, "ResetTimeout", "13 minutes");
        assertJmxValue(oNameCB, "MaxResetTimeout", "14 days");
        assertJmxValue(oNameCB, "ExponentialBackoffFactor", 16.0);
    }

    private void assertJmxValue(ObjectName oName, String key, Object expectedValue) throws Exception {
        Object actualValue = ManagementFactory.getPlatformMBeanServer().getAttribute(oName, key);
        assertEquals(oName + "." + key, expectedValue, actualValue);
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

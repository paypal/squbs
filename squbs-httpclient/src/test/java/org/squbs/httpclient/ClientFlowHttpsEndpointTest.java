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
import org.apache.pekko.http.javadsl.ConnectionContext;
import org.apache.pekko.http.javadsl.HostConnectionPool;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.japi.Pair;
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;

public class ClientFlowHttpsEndpointTest {

    private static final String cfg =
            "helloHttps {\n" +
            "  type = squbs.httpclient\n" +
            "  pekko.ssl-config.loose.disableHostnameVerification = true\n" +
            "}\n";

    private static final ActorSystem system = ActorSystem.create("ClientFlowHttpsEndpointTest",
            ConfigFactory.parseString(cfg));
    private static final Route route = new MyRoute().route();
    private static final Materializer mat = Materializer.createMaterializer(system);
    private static final ServerBinding serverBinding;

    static {
        ServerBinding binding;
        try {
            binding = Http.get(system)
                    .newServerAt("localhost", 0)
                    .enableHttps(ConnectionContext.httpsServer(sslContext("example.com.jks", "changeit")))
                    .bind(route)
                    .toCompletableFuture()
                    .get();
        } catch(Exception e) {
            binding = null;
        }
        serverBinding = binding;
    }

    private static final int port = serverBinding.localAddress().getPort();

    static class InsecureSSLEngineProvider implements SSLEngineProvider {

        @Override
        public SSLEngine createSSLEngine(SSLContext sslContext, String hostname, int port) {
            SSLEngine engine = sslContext.createSSLEngine(hostname, port);
            engine.setUseClientMode(true);
            return engine;
        }
    }

    static {
        Optional<SSLContext> scOpt;
        try {
            scOpt = Optional.of(sslContext("exampletrust.jks", "changeit"));
        } catch (Exception e) {
            e.printStackTrace();
            scOpt = Optional.empty();
        }
        final Optional<SSLContext> sslContextOpt = scOpt;
        ResolverRegistry.get(system).register(HttpEndpoint.class, "LocalhostHttpsEndpointResolver", (svcName, env) -> {
            if ("helloHttps".equals(svcName)) {
                return Optional.of(HttpEndpoint.create("https://localhost:" + port,
                        sslContextOpt,
                        Optional.empty(),
                        Optional.of(new InsecureSSLEngineProvider())
                ));
            }
            return Optional.empty();
        });
    }

    private final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow;

    public ClientFlowHttpsEndpointTest() {
        clientFlow = ClientFlow.create("helloHttps", system, mat);
    }

    static SSLContext sslContext(String store, String pw) throws GeneralSecurityException, IOException {
            char[] password = pw.toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            InputStream keystore = ClientFlowHttpsTest.class.getClassLoader()
                    .getResourceAsStream("ClientFlowHttpsSpec/" + store);
            assert keystore != null;
            ks.load(keystore, password);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(ks, password);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
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

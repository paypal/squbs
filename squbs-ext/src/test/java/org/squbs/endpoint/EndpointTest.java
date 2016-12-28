/*
 *  Copyright 2015 PayPal
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
package org.squbs.endpoint;

import akka.actor.ActorSystem;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.squbs.env.Default;
import org.squbs.env.EnvTestHelper;
import org.squbs.env.Environment;
import scala.collection.immutable.List;
import scala.Option;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.junit.Assert.*;

public class EndpointTest {

    private static final ActorSystem system = ActorSystem.create("EndpointTest");

    @AfterClass
    public static void afterAll() {
        system.terminate();
    }

    @After
    public void clear() {
        EndpointTestHelper.clearRegistries(system);
        EnvTestHelper.clearRegistries(system);
    }

    @Test
    public void registerResolver() {
        AbstractEndpointResolver resolver = new DummyLocalhostJavaResolver();
        EndpointResolverRegistry.get(system).register(resolver);
        assertEquals(1, EndpointResolverRegistry.get(system).endpointResolvers().size());
        assertEquals("DummyLocalhostJavaResolver",
                EndpointResolverRegistry.get(system).endpointResolvers().head().name());
    }

    @Test
    public void registerLambdaResolver() {
        EndpointResolverRegistry.get(system).register("RogueResolver",
                (svcName, env) -> Optional.of(Endpoint.create("http://myrogueservice.com")));
        assertEquals(1, EndpointResolverRegistry.get(system).endpointResolvers().size());
        assertEquals("RogueResolver", EndpointResolverRegistry.get(system).endpointResolvers().head().name());
    }

    @Test
    public void registerResolveSSLResolver() {
        Optional<SSLContext> sslOption = Optional.empty();
        try {
            sslOption = Optional.of(SSLContext.getDefault());
        } catch (NoSuchAlgorithmException e) {
            // do nothing.
        }
        final Optional<SSLContext> ssl = sslOption;
        EndpointResolverRegistry.get(system).register("RogueResolver",
                (svcName, env) -> Optional.of(
                        Endpoint.create("https://myrogueservice.com", ssl)));
        assertEquals(1, EndpointResolverRegistry.get(system).endpointResolvers().size());
        assertEquals("RogueResolver", EndpointResolverRegistry.get(system).endpointResolvers().head().name());

        Option<EndpointResolver> resolver =
                EndpointResolverRegistry.get(system).findResolver("abcService", Default.value());
        assertEquals("RogueResolver", resolver.get().name());
        Endpoint endpoint = resolver.get().resolve("abcService", Default.value()).get();
        assertEquals("https://myrogueservice.com", endpoint.uri().toString());
        assertEquals(ssl.orElse(null), endpoint.sslContext().get());
    }

    @Test
    public void resolveEndpoint() {
        EndpointResolverRegistry.get(system).register(new DummyLocalhostJavaResolver());
        Option<EndpointResolver> resolver =
                EndpointResolverRegistry.get(system).findResolver("abcService", Default.value());
        assertEquals("DummyLocalhostJavaResolver", resolver.get().name());
        assertEquals("http://localhost:8080",
                resolver.get().resolve("abcService", Default.value()).get().uri().toString());
    }

    @Test
    public void resolveEndpointWithLambdaResolver() {
        EndpointResolverRegistry.get(system).register("RogueResolver",
                (svcName, env) -> Optional.of(Endpoint.create("http://myrogueservice.com")));
        Option<EndpointResolver> resolver =
                EndpointResolverRegistry.get(system).findResolver("abcService", Default.value());
        assertEquals("RogueResolver", resolver.get().name());
        assertEquals("http://myrogueservice.com",
                resolver.get().resolve("abcService", Default.value()).get().uri().toString());
    }

    @Test
    public void resolveInReverseOrder() {
        EndpointResolverRegistry.get(system).register(new DummyLocalhostJavaResolver());
        EndpointResolverRegistry.get(system).register(
                new AbstractEndpointResolver() {
                    @Override
                    public String name() {
                        return "OverrideResolver";
                    }

                    @Override
                    public Optional<Endpoint> resolve(String svcName, Environment env) {
                        return Optional.of(Endpoint.create("http://overrideservi.ce"));
                    }
                });
        List<EndpointResolver> resolvers = EndpointResolverRegistry.get(system).endpointResolvers();
        assertEquals(2, resolvers.size());
        assertNotSame(DummyLocalhostJavaResolver.class, resolvers.head().getClass());
        assertEquals("OverrideResolver", resolvers.head().name());
        Option<EndpointResolver> resolverOption = EndpointResolverRegistry.get(system)
                .findResolver("abcService", Default.value());
        assertTrue(resolverOption.isDefined());
        assertEquals("OverrideResolver", resolverOption.get().name());
        Option<Endpoint> endpointOption = EndpointResolverRegistry.get(system)
                .resolve("abcService", Default.value());
        assertEquals("http://overrideservi.ce", endpointOption.get().uri().toString());
    }

    @Test
    public void resolveMixedInReverseOrder() {
        EndpointResolverRegistry.get(system).register(new DummyLocalhostJavaResolver());
        EndpointResolverRegistry.get(system).register("OverrideResolver", (svcName, env) ->
                Optional.of(Endpoint.create("http://overrideservi.ce")));
        List<EndpointResolver> resolvers = EndpointResolverRegistry.get(system).endpointResolvers();
        assertEquals(2, resolvers.size());
        assertNotSame(DummyLocalhostJavaResolver.class, resolvers.head().getClass());
        assertEquals("OverrideResolver", resolvers.head().name());
        Option<EndpointResolver> resolverOption = EndpointResolverRegistry.get(system)
                .findResolver("abcService", Default.value());
        assertTrue(resolverOption.isDefined());
        assertEquals("OverrideResolver", resolverOption.get().name());
        Option<Endpoint> endpointOption = EndpointResolverRegistry.get(system)
                .resolve("abcService", Default.value());
        assertEquals("http://overrideservi.ce", endpointOption.get().uri().toString());
    }
}

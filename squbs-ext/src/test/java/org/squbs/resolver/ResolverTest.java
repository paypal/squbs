/*
 *  Copyright 2017 PayPal
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
package org.squbs.resolver;

import org.apache.pekko.actor.ActorSystem;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.squbs.env.Default;
import org.squbs.env.EnvTestHelper;
import org.squbs.env.Environment;
import scala.Tuple2;
import scala.collection.immutable.List;

import java.net.URI;
import java.util.Optional;

import static org.junit.Assert.*;

public class ResolverTest {

    private static final ActorSystem system = ActorSystem.create("ResolverTest");

    @AfterClass
    public static void afterAll() {
        system.terminate();
    }

    @After
    public void clear() {
        ResolverTestHelper.clearRegistries(system);
        EnvTestHelper.clearRegistries(system);
    }

    @Test
    public void registerResolver() {
        ResolverRegistry.get(system).register(URI.class, new DummyLocalhostJavaResolver());
        assertEquals(1, ResolverRegistry.get(system).resolvers().size());
        assertEquals("DummyLocalhostJavaResolver",
                ResolverRegistry.get(system).resolvers().head()._2().name());
    }

    @Test
    public void registerLambdaResolver() {
        ResolverRegistry.get(system).register(URI.class, "RogueResolver",
                (svcName, env) -> Optional.of(URI.create("http://localhost:8080")));
        assertEquals(1, ResolverRegistry.get(system).resolvers().size());
        assertEquals("RogueResolver", ResolverRegistry.get(system).resolvers().head()._2().name());
    }

    @Test
    public void resolveEndpoint() {
        ResolverRegistry.get(system).register(URI.class, new DummyLocalhostJavaResolver());
        Optional<Resolver<URI>> resolver =
                ResolverRegistry.get(system).findResolver(URI.class, "abcService", Default.value());
        assertEquals("DummyLocalhostJavaResolver", resolver.get().name());
        assertEquals("http://localhost:8080",  resolver.get().resolve("abcService", Default.value())
                .get().toString());
    }

    @Test
    public void resolveEndpointWithLambdaResolver() {
        ResolverRegistry.get(system).register(URI.class, "RogueResolver",
                (svcName, env) -> Optional.of(URI.create("http://myrogueservice.com")));
        Optional<Resolver<URI>> resolver =
                ResolverRegistry.get(system).findResolver(URI.class, "abcService", Default.value());
        assertEquals("RogueResolver", resolver.get().name());
        assertEquals("http://myrogueservice.com", resolver.get()
                .resolve("abcService", Default.value()).get().toString());
    }

    @Test
    public void resolveInReverseOrder() {
        ResolverRegistry.get(system).register(URI.class, new DummyLocalhostJavaResolver());
        ResolverRegistry.get(system).register(URI.class,
                new AbstractResolver<URI>() {
                    @Override
                    public String name() {
                        return "OverrideResolver";
                    }

                    @Override
                    public Optional<URI> resolve(String svcName, Environment env) {
                        return Optional.of(URI.create("http://overrideservi.ce"));
                    }
                });
        List<Tuple2<Class<?>, Resolver<?>>> resolvers = ResolverRegistry.get(system).resolvers();
        assertEquals(2, resolvers.size());
        Resolver firstResolver = resolvers.head()._2();

        assertNotSame(DummyLocalhostJavaResolver.class, firstResolver.getClass());
        assertEquals("OverrideResolver", firstResolver.name());
        Optional<Resolver<URI>> resolverOption = ResolverRegistry.get(system)
                .findResolver(URI.class, "abcService", Default.value());
        assertTrue(resolverOption.isPresent());
        assertEquals("OverrideResolver", resolverOption.get().name());
        Optional<URI> epOption = ResolverRegistry.get(system)
                .resolve(URI.class, "abcService", Default.value());
        assertEquals("http://overrideservi.ce", epOption.get().toString());
    }

    @Test
    public void resolveMixedInReverseOrder() {
        ResolverRegistry.get(system).register(URI.class, new DummyLocalhostJavaResolver());
        ResolverRegistry.get(system).register(URI.class, "OverrideResolver", (svcName, env) ->
                Optional.of(URI.create("http://overrideservi.ce")));

        List<Tuple2<Class<?>, Resolver<?>>> resolvers = ResolverRegistry.get(system).resolvers();

        assertEquals(2, resolvers.size());
        Resolver firstResolver = resolvers.head()._2();
        assertNotSame(DummyLocalhostJavaResolver.class, resolvers.head().getClass());
        assertEquals("OverrideResolver", firstResolver.name());
        Optional<Resolver<URI>> resolverOption = ResolverRegistry.get(system)
                .findResolver(URI.class, "abcService", Default.value());
        assertTrue(resolverOption.isPresent());
        assertEquals("OverrideResolver", resolverOption.get().name());
        Optional<URI> endpointOption = ResolverRegistry.get(system)
                .resolve(URI.class, "abcService", Default.value());
        assertEquals("http://overrideservi.ce", endpointOption.get().toString());
    }
}

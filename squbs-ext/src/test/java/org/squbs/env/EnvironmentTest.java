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
package org.squbs.env;

import org.apache.pekko.actor.ActorSystem;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EnvironmentTest {

    private static final ActorSystem system = ActorSystem.create("EnvironmentTest");

    @AfterClass
    public static void afterAll() {
        system.terminate();
    }

    @After
    public void clear() {
        EnvTestHelper.clearRegistries(system);
    }

    @Test
    public void resolveDefault() {
        EnvironmentResolverRegistry.get(system).register(DummyProdEnvironmentResolver.get());
        Environment env = EnvironmentResolverRegistry.get(system).resolve();
        assertEquals(PROD.value(), env);
    }

    @Test
    public void reverseOrder() {
        EnvironmentResolverRegistry.get(system).register(DummyProdEnvironmentResolver.get());
        EnvironmentResolverRegistry.get(system).register(DummyQAEnvironmentResolver.get());
        Environment env = EnvironmentResolverRegistry.get(system).resolve();
        assertEquals(QA.value(), env);
    }

    @Test
    public void keepTrying() {
        EnvironmentResolverRegistry.get(system).register(DummyProdEnvironmentResolver.get());
        EnvironmentResolverRegistry.get(system).register(DummyNotResolveEnvironmentResolver.get());
        Environment env = EnvironmentResolverRegistry.get(system).resolve();
        assertEquals(PROD.value(), env);
    }
}

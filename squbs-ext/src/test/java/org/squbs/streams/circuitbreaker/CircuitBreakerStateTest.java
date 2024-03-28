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

package org.squbs.streams.circuitbreaker;

import org.apache.pekko.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Duration;

public class CircuitBreakerStateTest {

    private static final ActorSystem system = ActorSystem.create("CircuitBreakerStateTest");

    @AfterClass
    public static void afterClass() {
        system.terminate();
    }

    @Test
    public void testCreateCircuitBreakerStateWithDefaultExponentialBackoffFactor() throws Exception {
        AtomicCircuitBreakerState.create(
                "java-params-with-default-exponential-backoff",
                1,
                Duration.ofMillis(50),
                Duration.ofMillis(20),
                system.dispatcher(),
                system.scheduler());
        assertJmxValue("java-params-with-default-exponential-backoff", "MaxFailures", 1);
        assertJmxValue("java-params-with-default-exponential-backoff", "CallTimeout", "50 milliseconds");
        assertJmxValue("java-params-with-default-exponential-backoff", "ResetTimeout", "20 milliseconds");
        assertJmxValue("java-params-with-default-exponential-backoff", "MaxResetTimeout", "36500 days");
        assertJmxValue("java-params-with-default-exponential-backoff", "ExponentialBackoffFactor", 1.0);
    }

    @Test
    public void testCreateCircuitBreakerStateWithExponentialBackoff() throws Exception {
        AtomicCircuitBreakerState.create(
                "java-params-with-custom-exponential-backoff", 1,
                Duration.ofMillis(50),
                Duration.ofMillis(20),
                Duration.ofMinutes(2),
                16.0,
                system.dispatcher(),
                system.scheduler());
        assertJmxValue("java-params-with-custom-exponential-backoff", "MaxFailures", 1);
        assertJmxValue("java-params-with-custom-exponential-backoff", "CallTimeout", "50 milliseconds");
        assertJmxValue("java-params-with-custom-exponential-backoff", "ResetTimeout", "20 milliseconds");
        assertJmxValue("java-params-with-custom-exponential-backoff", "MaxResetTimeout", "2 minutes");
        assertJmxValue("java-params-with-custom-exponential-backoff", "ExponentialBackoffFactor", 16.0);
    }

    @Test
    public void testCreateCircuitBreakerStateFromConfiguration() throws Exception {
        Config config = ConfigFactory.parseString(
                "max-failures = 1\n" +
                    "call-timeout = 50 ms\n" +
                    "reset-timeout = 20 ms\n" +
                    "max-reset-timeout = 1 minute\n" +
                    "exponential-backoff-factor = 16.0");
        AtomicCircuitBreakerState.create("java-from-config", config, system);
        assertJmxValue("java-from-config", "MaxFailures", 1);
        assertJmxValue("java-from-config", "CallTimeout", "50 milliseconds");
        assertJmxValue("java-from-config", "ResetTimeout", "20 milliseconds");
        assertJmxValue("java-from-config", "MaxResetTimeout", "1 minute");
        assertJmxValue("java-from-config", "ExponentialBackoffFactor", 16.0);
    }

    @Test
    public void testCreateCircuitBreakerStateWithDefaultConfig() throws Exception {
        AtomicCircuitBreakerState.create("java-default-config", ConfigFactory.empty(), system);
        assertJmxValue("java-default-config", "MaxFailures", 5);
        assertJmxValue("java-default-config", "CallTimeout", "1 second");
        assertJmxValue("java-default-config", "ResetTimeout", "5 seconds");
        assertJmxValue("java-default-config", "MaxResetTimeout", "36500 days");
        assertJmxValue("java-default-config", "ExponentialBackoffFactor", 1.0);
    }

    private void assertJmxValue(String name, String key, Object expectedValue) throws Exception {
        ObjectName oName = ObjectName.getInstance(
                "org.squbs.configuration:type=squbs.circuitbreaker,name=" + ObjectName.quote(name));
        Object actualValue = ManagementFactory.getPlatformMBeanServer().getAttribute(oName, key);
        Assert.assertEquals(expectedValue, actualValue);
    }
}

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

package org.squbs.testkit.japi;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CustomTestKitResourcesJUnitTest extends CustomTestKit {

    public CustomTestKitResourcesJUnitTest() {
        super(TestConfig.resources, false);
    }

    @After
    public void tearDown() {
        shutdown();
    }

    @Test
    public void testDefaultListenerStarted() {
         Assert.assertEquals(port("default-listener"), port());
    }

    @Test
    public void testActorSystemName() {
        Assert.assertTrue(system().name().matches("org-squbs-testkit-japi-CustomTestKitResourcesJUnitTest-\\d+"));
    }

    private static class TestConfig {
        private static List<String> resources = Arrays.asList(
                TestConfig.class.getClassLoader().getResource("").getPath() + "/CustomTestKitTest/META-INF/squbs-meta.conf"
        );
    }
}

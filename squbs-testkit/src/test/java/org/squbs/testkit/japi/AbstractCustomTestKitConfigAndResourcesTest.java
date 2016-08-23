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

package org.squbs.testkit.japi;

import com.typesafe.config.ConfigFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class AbstractCustomTestKitConfigAndResourcesTest extends AbstractCustomTestKit {

    AbstractCustomTestKitConfigAndResourcesTest() {
        super(ConfigFactory.parseString("squbs.actorsystem-name = AbstractCustomTestKitConfigAndResourcesTestInConfig"),
                TestConfig.resources, false);
    }

    @Test
    public void testDefaultListenerStarted() {
         Assert.assertEquals(port(), port("default-listener"));
    }

    @Test
    public void testActorSystemName() {
        Assert.assertEquals(system().name(), "AbstractCustomTestKitConfigAndResourcesTestInConfig");
    }

    private static class TestConfig {
        private static List<String> resources = Arrays.asList(
                TestConfig.class.getClassLoader().getResource("").getPath() + "/CustomTestKitTest/META-INF/squbs-meta.conf"
        );
    }
}

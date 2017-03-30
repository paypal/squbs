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

import org.squbs.unicomplex.JMX;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

public class CustomTestKitDefaultWithActorSystemNameTest extends CustomTestKit {

    CustomTestKitDefaultWithActorSystemNameTest() {
        super("CustomTestKitDefaultWithActorSystemNameTest");
    }

    @AfterClass
    public void tearDown() {
        shutdown();
    }

    @Test
    public void testDefaultListenerStarted() {
        Assert.assertEquals(port(), port("default-listener"));
    }

    @Test
    public void testActorSystemName() {
        Assert.assertEquals(system().name(), "CustomTestKitDefaultWithActorSystemNameTest");
    }

    @Test
    public void testDefaultConfigurationInUse() {
        Assert.assertEquals(system().settings().config().getInt("default-listener.bind-port"), 0);
        Assert.assertEquals(system().settings().config().getBoolean("squbs." + JMX.prefixConfig()), true);
    }
}

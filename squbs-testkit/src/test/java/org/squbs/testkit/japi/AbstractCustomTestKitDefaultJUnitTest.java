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

import org.junit.Assert;
import org.junit.Test;
import org.squbs.unicomplex.JMX;

public class AbstractCustomTestKitDefaultJUnitTest extends AbstractCustomTestKit {

    @Test
    public void testDefaultListenerStarted() {
        Assert.assertEquals(port("default-listener"), port());
    }

    @Test
    public void testActorSystemName() {
        Assert.assertTrue(system().name().matches("org-squbs-testkit-japi-AbstractCustomTestKitDefaultJUnitTest-\\d+"));
    }

    @Test
    public void testDefaultConfigurationInUse() {
        Assert.assertEquals(0, system().settings().config().getInt("default-listener.bind-port"));
        Assert.assertEquals(true, system().settings().config().getBoolean("squbs." + JMX.prefixConfig()));
    }
}

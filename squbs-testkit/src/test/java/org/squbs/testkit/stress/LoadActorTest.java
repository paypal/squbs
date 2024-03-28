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

package org.squbs.testkit.stress;

import org.apache.pekko.actor.*;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.squbs.testkit.TestActorJ;
import scala.concurrent.duration.FiniteDuration;

import java.util.List;

public class LoadActorTest{

    static ActorSystem system;

    @BeforeClass
    public static void beforeClass() {
        system = ActorSystem.create("LoadActorTest");
    }

    @AfterClass
    public static void afterAll() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testIt() {
        new TestKit(system) {{
            FiniteDuration warmUp = duration("20 seconds");
            FiniteDuration steady = duration("40 seconds");
            int ir = 500;
            long startTime = System.nanoTime();
            ActorRef loadActor = system.actorOf(Props.create(LoadActor.class));
            loadActor.tell(new StartLoad(startTime, ir, warmUp, steady, () -> {
                system.actorOf(Props.create(TestActorJ.class)).tell("Ping", getRef());
            }), getRef());

            // just verify the LoadFn
            List<Object> response = receiveN(10);

            for (Object r : response) {
                System.out.println(r);
                Assert.assertEquals("Pong", r);
            }
            loadActor.tell(PoisonPill.getInstance(), getRef());
        }};
    }
}

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

package org.squbs.pattern.orchestration.japi;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.util.Timeout;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Test;
import org.squbs.testkit.japi.CustomTestKit;
import org.squbs.testkit.japi.DebugTimingTestKit;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.squbs.pattern.orchestration.japi.Messages.*;

public class TimedOutOrchestratorTest extends CustomTestKit {

    public TimedOutOrchestratorTest() {
        super(ConfigFactory.parseString("squbs.external-config-dir = TimedOutOrchestratorTestConfig"));
    }

    @After
    public void tearDown() {
        shutdown();
    }

    @Test
    public void testResultAfterFinish() {
        new DebugTimingTestKit(system()) {{
            ActorRef orchestrator = getSystem().actorOf(Props.create(AskOrchestrator.class,
                    new FiniteDuration(500, TimeUnit.MILLISECONDS),
                    new Timeout(10, TimeUnit.MILLISECONDS)));
            orchestrator.tell(new TestRequest("test"), getRef());

            // Check for the submitted message
            SubmittedOrchestration submitted = expectMsgClass(SubmittedOrchestration.class);
            long submitTime = submitted.timeNs / 1000L;
            assertTrue(submitTime / 1000L < 230000L);
            assertEquals("test", submitted.message);

            // Check for the finished message
            FinishedOrchestration finished = expectMsgClass(FinishedOrchestration.class);
            long finishTime = finished.timeNs / 1000L;
            assertTrue(finishTime > 30000L); // 3 orchestrations with 10 millisecond delay each
            assertEquals("test", finished.message);
            // All orchestrator functions should time out, returning -1 in all cases, making it -3.
            assertEquals(-3L, finished.lastId);
        }};
    }
}

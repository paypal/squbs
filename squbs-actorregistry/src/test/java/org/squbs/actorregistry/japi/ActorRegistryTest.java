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
package org.squbs.actorregistry.japi;

import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.Identify;
import akka.actor.PoisonPill;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.squbs.actorregistry.ActorNotFound;
import org.squbs.actorregistry.ActorRegistryBean;
import org.squbs.actorregistry.testcube.TestRequest;
import org.squbs.actorregistry.testcube.TestRequest1;
import org.squbs.actorregistry.testcube.TestResponse;
import org.squbs.testkit.japi.CustomTestKit;
import org.squbs.testkit.japi.DebugTimingTestKit;
import org.squbs.unicomplex.JMX;
import org.squbs.unicomplex.UnicomplexBoot;
import scala.concurrent.Await;
import scala.concurrent.Future;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.*;
import static org.squbs.testkit.Timeouts.*;

public class ActorRegistryTest {

    private static CustomTestKit testKit;
    private static ActorLookup<?> lookup;
    private static UnicomplexBoot boot;

    @BeforeClass
    public static void beforeAll() throws IOException {
        String dummyJarsDir = Optional.ofNullable(ActorRegistryTest.class.getClassLoader().getResource("classpaths"))
                .map(URL::getPath).orElseGet(() -> "");
        String[] dummyCubes = {"ActorRegistryCube", "TestCube"};
        String[] classPaths = Arrays.stream(dummyCubes).map(cp -> dummyJarsDir + "/" + cp).toArray(String[]::new);
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("squbs.actorsystem-name", "ActorRegistryTest");
        configMap.put("squbs.prefix-jmx-name", Boolean.TRUE);
        Config testConfig = ConfigFactory.parseMap(configMap);
        boot = UnicomplexBoot.apply(testConfig)
                .scanComponents(classPaths).initExtensions().start();
        testKit = new CustomTestKit(boot);
        lookup = ActorLookup.create(testKit.actorSystem());
    }

    @AfterClass
    public static void afterAll() {
        testKit.shutdown();
    }

    @Test
    public void testSimpleTell() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
                lookup.tell(new TestRequest("ActorLookup"), getRef());
                assertEquals(new TestResponse("ActorLookup"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testSimpleAsk() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            CompletionStage<?> stage = lookup.ask(new TestRequest("ActorLookup"), askTimeout());

            try {
                assertEquals(new TestResponse("ActorLookup"), stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testSimpleAskWithMsTimeout() {
        long timeout = askTimeout().duration().toMillis();
        new DebugTimingTestKit(testKit.actorSystem()) {{
            CompletionStage<?> stage = lookup.ask(new TestRequest("ActorLookup"), timeout);

            try {
                assertEquals(new TestResponse("ActorLookup"), stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test(expected = ActorNotFound.class)
    public void testResolveOne() throws Exception {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            Future<ActorRef> future = lookup.resolveOne(askTimeout().duration());
            Await.result(future, awaitMax());
        }};
    }

    @Test
    public void testResolveOneByName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            Future<ActorRef> future = lookup.lookup("TestActor").resolveOne(askTimeout().duration());
            try {
                ActorRef actorRef = Await.result(future, awaitMax());
                assertEquals("TestActor", actorRef.path().name());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testResolveOneByRequestType() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            Future<ActorRef> future = lookup.lookup(Optional.empty(), Optional.of(TestRequest.class),
                    Object.class).resolveOne(askTimeout().duration());
            try {
                ActorRef actorRef = Await.result(future, awaitMax());
                assertEquals("TestActor", actorRef.path().name());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testResolveOneByType() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            Future<ActorRef> future = lookup.lookup(TestResponse.class).resolveOne(askTimeout().duration());
            try {
                ActorRef actorRef = Await.result(future, awaitMax());
                assertEquals("TestActor", actorRef.path().name());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testTellByType() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup(TestResponse.class).tell(new TestRequest("ActorLookup[TestResponse]"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByType() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            CompletionStage<TestResponse> stage = lookup.lookup(TestResponse.class)
                    .ask(new TestRequest("ActorLookup[TestResponse]"), askTimeout());
            try {
                assertEquals(new TestResponse("ActorLookup[TestResponse]"), stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testTellByName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup("TestActor").tell(new TestRequest("ActorLookup[TestResponse]"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            CompletionStage<?> stage = lookup.lookup("TestActor")
                    .ask(new TestRequest("ActorLookup[TestResponse]"), askTimeout());
            try {
                assertEquals(new TestResponse("ActorLookup[TestResponse]"), stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testTellByOptionalName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup(Optional.of("TestActor")).tell(new TestRequest("ActorLookup[TestResponse]"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskOptionalName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            CompletionStage<?> stage = lookup.lookup(Optional.of("TestActor"))
                    .ask(new TestRequest("ActorLookup[TestResponse]"), askTimeout());
            try {
                assertEquals(new TestResponse("ActorLookup[TestResponse]"), stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }


    @Test
    public void testTellByTypeAndName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup("TestActor", TestResponse.class)
                    .tell(new TestRequest("ActorLookup[TestResponse]('TestActor')"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]('TestActor')"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByTypeAndName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            CompletionStage<TestResponse> stage = lookup.lookup("TestActor", TestResponse.class)
                    .ask(new TestRequest("ActorLookup[TestResponse]('TestActor')"), askTimeout());
            try {
                assertEquals(new TestResponse("ActorLookup[TestResponse]('TestActor')"),
                        stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testTellByTypeAndOptionalName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup(Optional.of("TestActor"), TestResponse.class)
                    .tell(new TestRequest("ActorLookup[TestResponse]('TestActor')"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]('TestActor')"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByTypeAndOptionalName() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            CompletionStage<TestResponse> stage = lookup.lookup(Optional.of("TestActor"), TestResponse.class)
                    .ask(new TestRequest("ActorLookup[TestResponse]('TestActor')"), askTimeout());
            try {
                assertEquals(new TestResponse("ActorLookup[TestResponse]('TestActor')"),
                        stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testIdentifyByType() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup(TestResponse.class).tell(new Identify(42), getRef());
            ActorIdentity id = ((ActorIdentity) receiveOne(awaitMax()));
            assertEquals("TestActor", id.ref().get().path().name());
        }};
    }

    @Test
    public void testLookupNonexistent() {
        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup(String.class).tell("NotExist", getRef());
            assertTrue(receiveOne(awaitMax()) instanceof ActorNotFound);
        }};
    }

    private static ObjectName getObjName(String name) throws MalformedObjectNameException {
        return new ObjectName(JMX.prefix(boot.actorSystem()) + ActorRegistryBean.Pattern() + name);
    }

    private static Optional<Object> getActorRegistryBean(String actorName, String att) {
        try {
            return Optional.of(ManagementFactory.getPlatformMBeanServer().getAttribute(getObjName(actorName), att));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Test
    public void testLookupNonexistentResponseType() {
        Optional<Object> before = ActorRegistryTest.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
        assertTrue(before.isPresent());

        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup("TestActor1", String.class).tell(new TestRequest1("13"), getRef());
            assertTrue(receiveOne(awaitMax()) instanceof ActorNotFound);
        }};
    }

    @Test
    public void testLookupExistingType() {
        Optional<Object> before = ActorRegistryTest.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
        assertTrue(before.isPresent());

        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup("TestActor1").tell(new TestRequest1("13"), getRef());
            assertTrue(receiveOne(awaitMax()) instanceof TestResponse);
        }};
    }

    @Test
    public void testLookupWithPoisonPill() {
        Optional<Object> before = ActorRegistryTest.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
        assertTrue(before.isPresent());

        new DebugTimingTestKit(testKit.actorSystem()) {{
            lookup.lookup("TestActor1").tell(PoisonPill.getInstance(), getRef());
            new AwaitAssert(awaitMax()) {
                protected void check() {
                    Optional<Object> after =
                            ActorRegistryTest.getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
                    assertFalse(after.isPresent());
                }
            };
        }};
    }
}

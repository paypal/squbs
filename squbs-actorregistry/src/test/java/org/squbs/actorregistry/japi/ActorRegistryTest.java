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
package org.squbs.actorregistry.japi;

import org.apache.pekko.actor.ActorIdentity;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Identify;
import org.apache.pekko.actor.PoisonPill;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.squbs.testkit.japi.Timeouts.askTimeout;
import static org.squbs.testkit.japi.Timeouts.awaitMax;

public class ActorRegistryTest extends CustomTestKit {

    private static final String[] resources;

    static {
        final String dummyJarsDir = Optional.ofNullable(ActorRegistryTest.class.getClassLoader()
                .getResource("classpaths")).map(URL::getPath).orElse("");
        final String[] dummyCubes = {"ActorRegistryCube", "TestCube"};
        resources = Arrays.stream(dummyCubes).map(cp ->
                dummyJarsDir + "/" + cp + "/META-INF/squbs-meta.conf").toArray(String[]::new);
    }

    private static CustomTestKit instance;
    private final ActorLookup<?> lookup;

    private static UnicomplexBoot start() {
        if (instance == null) {
            Config testConfig = ConfigFactory.parseString(
                    "squbs.actorsystem-name = ActorRegistryCube\n" +
                    "squbs.prefix-jmx-name = true\n"
            );
            return UnicomplexBoot.apply(testConfig).scanResources(false, resources).initExtensions().start();
        } else {
            return instance.boot();
        }
    }

    public ActorRegistryTest() {
        super(start());
        instance = this;
        lookup = ActorLookup.create(system());
    }

    @AfterClass
    public static void tearDown() {
        instance.shutdown();
    }

    @Test
    public void testSimpleTell() {
        new DebugTimingTestKit(system()) {{
                lookup.tell(new TestRequest("ActorLookup"), getRef());
                assertEquals(new TestResponse("ActorLookup"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testSimpleAsk() {
        new DebugTimingTestKit(system()) {{
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
        new DebugTimingTestKit(system()) {{
            CompletionStage<?> stage = lookup.ask(new TestRequest("ActorLookup"), timeout);

            try {
                assertEquals(new TestResponse("ActorLookup"), stage.toCompletableFuture().get());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testResolveOne() {
        new DebugTimingTestKit(system()) {{
            try {
                CompletionStage<ActorRef> future = lookup.resolveOne(askTimeout().duration());
                future.toCompletableFuture().get();
                fail("Expecting an ExecutionException");
            } catch (Exception e) {
                assertEquals(ExecutionException.class, e.getClass());
                assertEquals(ActorNotFound.class, e.getCause().getClass());
            }
        }};
    }

    @Test
    public void testResolveOneByName() {
        new DebugTimingTestKit(system()) {{
            CompletionStage<ActorRef> future = lookup.lookup("TestActor").resolveOne(askTimeout().duration());
            try {
                ActorRef actorRef = future.toCompletableFuture().get();
                assertEquals("TestActor", actorRef.path().name());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testResolveOneByRequestType() {
        new DebugTimingTestKit(system()) {{
            CompletionStage<ActorRef> future = lookup.lookup(Optional.empty(), Optional.of(TestRequest.class),
                    Object.class).resolveOne(askTimeout().duration());
            try {
                ActorRef actorRef = future.toCompletableFuture().get();
                assertEquals("TestActor", actorRef.path().name());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testResolveOneByType() {
        new DebugTimingTestKit(system()) {{
            CompletionStage<ActorRef> future = lookup.lookup(TestResponse.class).resolveOne(askTimeout().duration());
            try {
                ActorRef actorRef = future.toCompletableFuture().get();
                assertEquals("TestActor", actorRef.path().name());
            } catch (Exception e) {
                fail("Awaiting for response");
            }
        }};
    }

    @Test
    public void testTellByType() {
        new DebugTimingTestKit(system()) {{
            lookup.lookup(TestResponse.class).tell(new TestRequest("ActorLookup[TestResponse]"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByType() {
        new DebugTimingTestKit(system()) {{
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
        new DebugTimingTestKit(system()) {{
            lookup.lookup("TestActor").tell(new TestRequest("ActorLookup[TestResponse]"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByName() {
        new DebugTimingTestKit(system()) {{
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
        new DebugTimingTestKit(system()) {{
            lookup.lookup(Optional.of("TestActor")).tell(new TestRequest("ActorLookup[TestResponse]"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskOptionalName() {
        new DebugTimingTestKit(system()) {{
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
        new DebugTimingTestKit(system()) {{
            lookup.lookup("TestActor", TestResponse.class)
                    .tell(new TestRequest("ActorLookup[TestResponse]('TestActor')"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]('TestActor')"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByTypeAndName() {
        new DebugTimingTestKit(system()) {{
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
        new DebugTimingTestKit(system()) {{
            lookup.lookup(Optional.of("TestActor"), TestResponse.class)
                    .tell(new TestRequest("ActorLookup[TestResponse]('TestActor')"), getRef());
            assertEquals(new TestResponse("ActorLookup[TestResponse]('TestActor')"), receiveOne(awaitMax()));
        }};
    }

    @Test
    public void testAskByTypeAndOptionalName() {
        new DebugTimingTestKit(system()) {{
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
        new DebugTimingTestKit(system()) {{
            lookup.lookup(TestResponse.class).tell(new Identify(42), getRef());
            ActorIdentity id = ((ActorIdentity) receiveOne(awaitMax()));
            assertEquals("TestActor", id.ref().get().path().name());
        }};
    }

    @Test
    public void testLookupNonexistent() {
        new DebugTimingTestKit(system()) {{
            lookup.lookup(String.class).tell("NotExist", getRef());
            assertTrue(receiveOne(awaitMax()) instanceof ActorNotFound);
        }};
    }

    private ObjectName getObjName(String name) throws MalformedObjectNameException {
        return new ObjectName(JMX.prefix(system()) + ActorRegistryBean.Pattern() + name);
    }

    private Optional<Object> getActorRegistryBean(String actorName, String att) {
        try {
            return Optional.of(ManagementFactory.getPlatformMBeanServer().getAttribute(getObjName(actorName), att));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Test
    public void testLookupNonexistentResponseType() {
        Optional<Object> before = getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
        assertTrue(before.isPresent());

        new DebugTimingTestKit(system()) {{
            lookup.lookup("TestActor1", String.class).tell(new TestRequest1("13"), getRef());
            assertTrue(receiveOne(awaitMax()) instanceof ActorNotFound);
        }};
    }

    @Test
    public void testLookupExistingType() {
        Optional<Object> before = getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
        assertTrue(before.isPresent());

        new DebugTimingTestKit(system()) {{
            lookup.lookup("TestActor1").tell(new TestRequest1("13"), getRef());
            assertTrue(receiveOne(awaitMax()) instanceof TestResponse);
        }};
    }

    @Test
    public void testLookupWithPoisonPill() {
        Optional<Object> before = getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
        assertTrue(before.isPresent());

        new DebugTimingTestKit(system()) {{
            lookup.lookup("TestActor1").tell(PoisonPill.getInstance(), getRef());
            awaitAssert(awaitMax(), () -> {
                Optional<Object> after = getActorRegistryBean("TestCube/TestActor1", "ActorMessageTypeList");
                assertFalse(after.isPresent());
                return null;
            });
        }};
    }
}

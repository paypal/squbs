package org.squbs.lifecycle;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class GracefulStopHelperTest {

    static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        system = ActorSystem.create("GracefulStopHelperTest");
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }


    @Test
    public void testStopFailed() {
        new TestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(BadShutdownActor.class));
            actor.tell("Stop", getRef());
            expectMsgEquals("Done");
        }};
    }

    @Test
    public void testStopSuccess() {
        new TestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(GoodShutdownActor.class));
            actor.tell("Stop", getRef());
            expectMsgEquals("Done");
        }};
    }

    static class BadShutdownActor extends ActorWithGracefulStopHelper {

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchEquals("Stop", x -> {
                        defaultMidActorStop(Collections.singletonList(getSelf()));
                        getSender().tell("Done", getSelf());
                    })
                    .build();
        }

        @Override
        public CompletionStage<Boolean> gracefulStop(ActorRef target, Duration duration, Object stopMessage) {
            return CompletableFuture.supplyAsync(() -> { throw new RuntimeException("BadMan"); });
        }
    }

    static class GoodShutdownActor extends ActorWithGracefulStopHelper {

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchEquals("Stop", x -> {
                        defaultMidActorStop(Collections.singletonList(getSelf()));
                        getSender().tell("Done", getSelf());
                    })
                    .build();
        }

        @Override
        public CompletionStage<Boolean> gracefulStop(ActorRef target, Duration duration, Object stopMessage) {
            return CompletableFuture.supplyAsync(() -> true);
        }
    }
}

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

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.BidiFlow;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.squbs.streams.DelayActor;
import org.squbs.streams.FlowTimeoutException;
import org.squbs.streams.Timing;
import org.squbs.streams.circuitbreaker.impl.AtomicCircuitBreakerState;
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.apache.pekko.pattern.Patterns.ask;

public class CircuitBreakerTest {

    private static final ActorSystem system = ActorSystem.create("CircuitBreakerTest");
    private static final Materializer mat = ActorMaterializer.create(system);
    private static final Duration timeout = Duration.ofMillis(300);
    private static final Try<String> timeoutFailure = Failure.apply(new FlowTimeoutException("Flow timed out!"));

    @AfterClass
    public static void afterClass() {
        system.terminate();
    }

    @Test
    public void testIncrementFailureCountOnCallTimeout() {
        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, UUID>)elem);


        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaIncFailCount",
                        2,
                        timeout,
                        Duration.ofMillis(10),
                        system.dispatcher(),
                        system.scheduler());

        final CircuitBreakerSettings<String, String, UUID> circuitBreakerSettings =
                CircuitBreakerSettings.create(circuitBreakerState);
        final ActorRef ref = Flow.<String>create()
                .map(s -> Pair.create(s, UUID.randomUUID()))
                .via(CircuitBreaker.create(circuitBreakerSettings).join(flow))
                .to(Sink.ignore())
                .runWith(Source.actorRef(25, OverflowStrategy.fail()), mat);


        new TestKit(system) {{
            circuitBreakerState.subscribe(getRef(), Open.instance());
            ref.tell("a", ActorRef.noSender());
            ref.tell("b", ActorRef.noSender());
            ref.tell("b", ActorRef.noSender());
            expectMsgEquals(Open.instance());
        }};
    }

    @Test
    public void testIncrementFailureCountBasedOnTheProvidedFunction() {
        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, UUID>)elem);

        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaFailureDecider",
                        2,
                        Duration.ofSeconds(10),
                        Duration.ofMillis(10),
                        system.dispatcher(),
                        system.scheduler());

        final CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.<String, String, UUID>create(circuitBreakerState)
                        .withFailureDecider(out -> out.get().equals("c"));

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed>
                circuitBreakerBidiFlow = CircuitBreaker.create(circuitBreakerSettings);


        final ActorRef ref = Flow.<String>create()
                .map(s -> Pair.create(s, UUID.randomUUID()))
                .via(circuitBreakerBidiFlow.join(flow))
                .to(Sink.ignore())
                .runWith(Source.actorRef(25, OverflowStrategy.fail()), mat);


        new TestKit(system) {{
            circuitBreakerState.subscribe(getRef(), TransitionEvents.instance());
            ref.tell("c", ActorRef.noSender());
            ref.tell("c", ActorRef.noSender());
            expectMsgEquals(Open.instance());
            expectMsgEquals(HalfOpen.instance());
            ref.tell("a", ActorRef.noSender());
            expectMsgEquals(Closed.instance());
        }};
    }

    @Test
    public void testRespondWithFallback() throws ExecutionException, InterruptedException {

        class IdGen {
            private Integer counter = 0;
            public Integer next() {
                return ++counter;
            }
        }

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, Integer>, Pair<String, Integer>, NotUsed> flow =
                Flow.<Pair<String, Integer>>create()
                        .mapAsyncUnordered(2, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, Integer>)elem);

        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaFallback",
                        2,
                        Duration.ofMillis(10),
                        Duration.ofSeconds(10),
                        system.dispatcher(),
                        system.scheduler());

        final CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.<String, String, Integer>create(circuitBreakerState)
                        .withFallback(s -> Success.apply("fb"));

        final BidiFlow<Pair<String, Integer>,
                       Pair<String, Integer>,
                       Pair<String, Integer>,
                       Pair<Try<String>, Integer>,
                       NotUsed>
                circuitBreakerBidiFlow = CircuitBreaker.create(circuitBreakerSettings);

        IdGen idGen = new IdGen();
        final CompletionStage<List<Pair<Try<String>, Integer>>> result =
                Source.from(Arrays.asList("b", "b", "b", "b"))
                        .map(s -> new Pair<>(s, idGen.next()))
                        .via(circuitBreakerBidiFlow.join(flow))
                        .runWith(Sink.seq(), mat);
        final List<Pair<Try<String>, Integer>> expected =
                Arrays.asList(
                        Pair.create(timeoutFailure, 1),
                        Pair.create(timeoutFailure, 2),
                        Pair.create(Success.apply("fb"), 3),
                        Pair.create(Success.apply("fb"), 4)
                );
        Assert.assertTrue(result.toCompletableFuture().get().containsAll(expected));
    }



    @Test
    public void testWithUniqueIdMapper() throws ExecutionException, InterruptedException {

        final AtomicInteger counter = new AtomicInteger(0);

        class MyContext {
            private String s;
            private long id;

            public MyContext(String s, long id) {
                this.s = s;
                this.id = id;
            }

            public long id() {
                return id;
            }

            @Override
            public int hashCode() { return counter.incrementAndGet(); } // On purpose, a problematic hashcode

            @Override
            public boolean equals(Object obj) {
                if(obj instanceof MyContext) {
                    MyContext mc = (MyContext)obj;
                    return mc.s.equals(s) && mc.id == id;
                } else return false;
            }
        }

        class IdGen {
            private long counter = 0;
            public long next() {
                return ++ counter;
            }
        }

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
                Flow.<Pair<String, MyContext>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, MyContext>)elem);

        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaUniqueId",
                        2,
                        timeout,
                        Duration.ofMillis(10),
                        system.dispatcher(),
                        system.scheduler());

        final CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.<String, String, MyContext>create(circuitBreakerState)
                        .withUniqueIdMapper(context -> context.id());

        final BidiFlow<Pair<String, MyContext>,
                       Pair<String, MyContext>,
                       Pair<String, MyContext>,
                       Pair<Try<String>, MyContext>,
                       NotUsed>
                circuitBreakerBidiFlow = CircuitBreaker.create(circuitBreakerSettings);

        IdGen idGen = new IdGen();
        final CompletionStage<List<Pair<Try<String>, MyContext>>> result =
                Source.from(Arrays.asList("a", "b", "b", "a"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", idGen.next())))
                        .via(circuitBreakerBidiFlow.join(flow))
                        .runWith(Sink.seq(), mat);
        final List<Pair<Try<String>, MyContext>> expected =
                Arrays.asList(
                        Pair.create(Success.apply("a"), new MyContext("dummy", 1)),
                        Pair.create(Success.apply("a"), new MyContext("dummy", 4)),
                        Pair.create(timeoutFailure, new MyContext("dummy", 2)),
                        Pair.create(timeoutFailure, new MyContext("dummy", 3))
                );
        Assert.assertTrue(result.toCompletableFuture().get().containsAll(expected));
    }

    @Test
    public void testWithCleanUp() throws ExecutionException, InterruptedException {

        final AtomicInteger counter = new AtomicInteger(0);

        class MyContext {
            private String s;
            private long id;

            public MyContext(String s, long id) {
                this.s = s;
                this.id = id;
            }

            public long id() {
                return id;
            }


            @Override
            public boolean equals(Object obj) {
                if(obj instanceof MyContext) {
                    MyContext mc = (MyContext)obj;
                    return mc.s.equals(s) && mc.id == id;
                } else return false;
            }
        }

        class IdGen {
            private long counter = 0;
            public long next() {
                return ++ counter;
            }
        }

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
                Flow.<Pair<String, MyContext>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, MyContext>)elem);

        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaUniqueId",
                        2,
                        Duration.ofSeconds(Timing.timeout().toSeconds()),
                        Duration.ofMillis(10),
                        system.dispatcher(),
                        system.scheduler());

        final CircuitBreakerSettings circuitBreakerSettings =
                CircuitBreakerSettings.<String, String, MyContext>create(circuitBreakerState)
                        .withCleanUp(s -> counter.incrementAndGet());

        final BidiFlow<Pair<String, MyContext>,
                Pair<String, MyContext>,
                Pair<String, MyContext>,
                Pair<Try<String>, MyContext>,
                NotUsed>
                circuitBreakerBidiFlow = CircuitBreaker.create(circuitBreakerSettings);

        IdGen idGen = new IdGen();
        final CompletionStage<List<Pair<Try<String>, MyContext>>> result =
                Source.from(Arrays.asList("a", "b", "b", "a"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", idGen.next())))
                        .via(circuitBreakerBidiFlow.join(flow))
                        .runWith(Sink.seq(), mat);
        final List<Pair<Try<String>, MyContext>> expected =
                Arrays.asList(
                        Pair.create(Success.apply("a"), new MyContext("dummy", 1)),
                        Pair.create(Success.apply("a"), new MyContext("dummy", 4)),
                        Pair.create(timeoutFailure, new MyContext("dummy", 2)),
                        Pair.create(timeoutFailure, new MyContext("dummy", 3))
                );
        Assert.assertTrue(result.toCompletableFuture().get().containsAll(expected));
        Assert.assertEquals(2, counter.get());
    }

    @Test
    public void testWithFailureDecider() throws Exception {
        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaFailureDecider",
                        2,
                        timeout,
                        Duration.ofMillis(10),
                        system.dispatcher(),
                        system.scheduler());

        final Function<Try<String>, Boolean> failureDecider =
                out -> out.isFailure() || out.equals(Success.apply("b")); // treat "b" as a failure for retry

        final CircuitBreakerSettings<String, String, UUID> circuitBreakerSettings =
                CircuitBreakerSettings.<String, String, UUID>create(circuitBreakerState).withFailureDecider(failureDecider);

        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
        Flow.<Pair<String, UUID>>create()
                .map(elem -> (Pair<String, UUID>)elem);

        final ActorRef ref = Flow.<String>create()
                .map(s -> Pair.create(s, UUID.randomUUID()))
                .via(CircuitBreaker.create(circuitBreakerSettings).join(flow))
                .to(Sink.ignore())
                .runWith(Source.actorRef(25, OverflowStrategy.fail()), mat);

        new TestKit(system) {{
            circuitBreakerState.subscribe(getRef(), TransitionEvents.instance());
            ref.tell("a", ActorRef.noSender());
            ref.tell("b", ActorRef.noSender());
            ref.tell("b", ActorRef.noSender());
            expectMsgEquals(Open.instance());
            expectMsgEquals(HalfOpen.instance());
            ref.tell("a", ActorRef.noSender());
            expectMsgEquals(Closed.instance());
        }};
    }

}

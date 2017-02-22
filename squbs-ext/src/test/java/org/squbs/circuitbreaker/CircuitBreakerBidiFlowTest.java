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

package org.squbs.circuitbreaker;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.BidiFlow;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.JavaTestKit;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import org.squbs.circuitbreaker.impl.AtomicCircuitBreakerState;
import org.squbs.streams.DelayActor;
import org.squbs.streams.FlowTimeoutException;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static akka.pattern.PatternsCS.ask;

public class CircuitBreakerBidiFlowTest {

    final ActorSystem system = ActorSystem.create(
            "CircuitBreakerBidiFlowTest",
            ConfigFactory.parseString(
                    "sample-circuit-breaker {\n" +
                        "  type = squbs.circuitbreaker\n" +
                        "  max-failures = 1\n" +
                        "  call-timeout = 50 ms\n" +
                        "  reset-timeout = 20 ms\n" +
                        "}"));
    final Materializer mat = ActorMaterializer.create(system);
    final FiniteDuration timeout = FiniteDuration.apply(300, TimeUnit.MILLISECONDS);
    final Try<String> timeoutFailure = Failure.apply(new FlowTimeoutException("Flow timed out!"));

    @Test
    public void testIncrementFailureCountOnCallTimeout() {
        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, 5000))
                        .map(elem -> (Pair<String, UUID>)elem);


        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaIncFailCount",
                        system.scheduler(),
                        2,
                        timeout,
                        FiniteDuration.apply(10, TimeUnit.MILLISECONDS),
                        system.dispatcher());


        final ActorRef ref = Flow.<String>create()
                .map(s -> Pair.create(s, UUID.randomUUID()))
                .via(CircuitBreakerBidiFlow.<String, String, UUID>create(circuitBreakerState).join(flow))
                .to(Sink.ignore())
                .runWith(Source.actorRef(25, OverflowStrategy.fail()), mat);


        new JavaTestKit(system) {{
            circuitBreakerState.subscribe(getRef(), Open.instance());
            ref.tell("a", ActorRef.noSender());
            ref.tell("b", ActorRef.noSender());
            ref.tell("b", ActorRef.noSender());
            expectMsgEquals(Open.instance());
        }};
    }

    @Test
    public void testCreateCircuitBreakerFromConfiguration() {
        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, 5000))
                        .map(elem -> (Pair<String, UUID>)elem);


        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create("sample-circuit-breaker", system);


        final ActorRef ref = Flow.<String>create()
                .map(s -> Pair.create(s, UUID.randomUUID()))
                .via(CircuitBreakerBidiFlow.<String, String, UUID>create(circuitBreakerState).join(flow))
                .to(Sink.ignore())
                .runWith(Source.actorRef(25, OverflowStrategy.fail()), mat);


        new JavaTestKit(system) {{
            circuitBreakerState.subscribe(getRef(), TransitionEvents.instance());
            ref.tell("a", ActorRef.noSender());
            ref.tell("b", ActorRef.noSender());
            expectMsgEquals(Open.instance());
            expectMsgEquals(HalfOpen.instance());
            ref.tell("a", ActorRef.noSender());
            expectMsgEquals(Closed.instance());
        }};
    }

    @Test
    public void testIncrementFailureCountBasedOnTheProvidedFunction() {
        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, 5000))
                        .map(elem -> (Pair<String, UUID>)elem);

        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaFailureDecider",
                        system.scheduler(),
                        2,
                        FiniteDuration.apply(10, TimeUnit.SECONDS),
                        FiniteDuration.apply(10, TimeUnit.MILLISECONDS),
                        system.dispatcher());

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed>
                circuitBreakerBidiFlow = CircuitBreakerBidiFlow.create(
                        circuitBreakerState,
                        Optional.empty(),
                        Optional.of(pair -> pair.first().get().equals("c")));


        final ActorRef ref = Flow.<String>create()
                .map(s -> Pair.create(s, UUID.randomUUID()))
                .via(circuitBreakerBidiFlow.join(flow))
                .to(Sink.ignore())
                .runWith(Source.actorRef(25, OverflowStrategy.fail()), mat);


        new JavaTestKit(system) {{
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
                        .mapAsyncUnordered(2, elem -> ask(delayActor, elem, 5000))
                        .map(elem -> (Pair<String, Integer>)elem);

        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaFallback",
                        system.scheduler(),
                        2,
                        FiniteDuration.apply(10, TimeUnit.MILLISECONDS),
                        FiniteDuration.apply(10, TimeUnit.SECONDS),
                        system.dispatcher());

        final BidiFlow<Pair<String, Integer>, Pair<String, Integer>, Pair<String, Integer>, Pair<Try<String>, Integer>, NotUsed> circuitBreakerBidiFlow =
                CircuitBreakerBidiFlow.create(
                        circuitBreakerState,
                        Optional.of(pair -> Pair.create(Success.apply("fb"), pair.second())),
                        Optional.empty());

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
    public void testWithCustomUniqueIdFunction() throws ExecutionException, InterruptedException {

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
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, 5000))
                        .map(elem -> (Pair<String, MyContext>)elem);

        final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create(
                        "JavaUniqueId",
                        system.scheduler(),
                        2,
                        timeout,
                        FiniteDuration.apply(10, TimeUnit.MILLISECONDS),
                        system.dispatcher());

        final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> circuitBreakerBidiFlow =
                CircuitBreakerBidiFlow.create(circuitBreakerState, Optional.empty(), Optional.empty(), MyContext::id);

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
}

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

package org.squbs.streams;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.BidiFlow;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.Assert;
import org.junit.Test;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.time.Duration;

import static org.apache.pekko.pattern.Patterns.ask;
import static scala.compat.java8.JFunction.*;

public class TimeoutTest {

    final ActorSystem system = ActorSystem.create("TimeoutBidiFlowTest");
    final Materializer mat = ActorMaterializer.create(system);
    final Duration timeout = Duration.ofMillis(300);
    final Try<String> timeoutFailure = Failure.apply(new FlowTimeoutException("Flow timed out!"));

    @Test
    public void testFlowsWithMessageOrderGuarantee() throws ExecutionException, InterruptedException {
        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<String, String, NotUsed> flow =
                Flow.<String>create()
                        .mapAsync(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (String)elem);


        final BidiFlow<String, String, String, Try<String>, NotUsed> timeoutBidiFlow =
                TimeoutOrdered.create(timeout);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .via(timeoutBidiFlow.join(flow))
                        .runWith(Sink.seq(), mat);
        // "c" fails because of slowness of "b"
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), timeoutFailure, timeoutFailure);
        Assert.assertEquals(expected, result.toCompletableFuture().get());
    }

    @Test
    public void testFlowsWithMessageOrderGuaranteeAndCleanUp() throws ExecutionException, InterruptedException {
        final AtomicInteger counter = new AtomicInteger(0);

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<String, String, NotUsed> flow =
                Flow.<String>create()
                        .mapAsync(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (String)elem);

        Consumer<String> cleanUp = s -> counter.incrementAndGet();

        final BidiFlow<String, String, String, Try<String>, NotUsed> timeoutBidiFlow =
                TimeoutOrdered.create(Duration.ofSeconds(Timing.timeout().toSeconds()), cleanUp);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .via(timeoutBidiFlow.join(flow))
                        .runWith(Sink.seq(), mat);
        // "c" fails because of slowness of "b"
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), timeoutFailure, timeoutFailure);
        Assert.assertEquals(expected, result.toCompletableFuture().get());
        Assert.assertEquals(2, counter.get());
    }

    @Test
    public void testFlowsWithoutMessageOrderGuarantee() throws ExecutionException, InterruptedException {
        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, UUID>)elem);

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> timeoutBidiFlow =
                Timeout.create(timeout);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, UUID.randomUUID()))
                        .via(timeoutBidiFlow.join(flow))
                        .map(t -> t.first())
                        .runWith(Sink.seq(), mat);
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), timeoutFailure);
        Assert.assertTrue(result.toCompletableFuture().get().containsAll(expected));
    }

    @Test
    public void testWithUniqueIdMapper() throws ExecutionException, InterruptedException {

        final AtomicInteger counter = new AtomicInteger(0);

        class MyContext {
            private String s;
            private UUID uuid;

            public MyContext(String s, UUID uuid) {
                this.s = s;
                this.uuid = uuid;
            }

            @Override
            public int hashCode() { return counter.incrementAndGet(); } // On purpose, a problematic hashcode
        }

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
                Flow.<Pair<String, MyContext>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, MyContext>)elem);

        TimeoutSettings settings = TimeoutSettings.<String, String, MyContext>create(timeout)
                .withUniqueIdMapper(func(context -> context.uuid));
        final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> timeoutBidiFlow =
                Timeout.create(settings);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
                        .via(timeoutBidiFlow.join(flow))
                        .map(t -> t.first())
                        .runWith(Sink.seq(), mat);
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), timeoutFailure);
        List<Try<String>> actual = result.toCompletableFuture().get();
        Assert.assertEquals(3, actual.size());
        Assert.assertTrue(actual.containsAll(expected));
    }

    @Test
    public void testWithUniqueIdProvider() throws ExecutionException, InterruptedException {

        final AtomicInteger counter = new AtomicInteger(0);

        class MyContext implements UniqueId.Provider {
            private String s;
            private UUID uuid;

            public MyContext(String s, UUID uuid) {
                this.s = s;
                this.uuid = uuid;
            }

            @Override
            public Object uniqueId() {
                return uuid;
            }

            @Override
            public int hashCode() { return counter.incrementAndGet(); } // On purpose, a problematic hashcode
        }

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
                Flow.<Pair<String, MyContext>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, MyContext>)elem);


        final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> timeoutBidiFlow =
                Timeout.create(timeout);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
                        .via(timeoutBidiFlow.join(flow))
                        .map(t -> t.first())
                        .runWith(Sink.seq(), mat);
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), timeoutFailure);
        List<Try<String>> actual = result.toCompletableFuture().get();
        Assert.assertEquals(3, actual.size());
        Assert.assertTrue(actual.containsAll(expected));
    }

    @Test
    public void testWithUniqueIdEnvelope() throws ExecutionException, InterruptedException {

        final AtomicInteger counter = new AtomicInteger(0);

        class MyContext {
            private String s;

            public MyContext(String s) {
                this.s = s;
            }

            @Override
            public int hashCode() { return counter.incrementAndGet(); } // On purpose, a problematic hashcode
        }

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UniqueId.Envelope>, Pair<String, UniqueId.Envelope>, NotUsed> flow =
                Flow.<Pair<String, UniqueId.Envelope>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, UniqueId.Envelope>)elem);


        final BidiFlow<Pair<String, UniqueId.Envelope>, Pair<String, UniqueId.Envelope>, Pair<String, UniqueId.Envelope>, Pair<Try<String>, UniqueId.Envelope>, NotUsed> timeoutBidiFlow =
                Timeout.create(timeout);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, new UniqueId.Envelope(new MyContext("dummy"), UUID.randomUUID())))
                        .via(timeoutBidiFlow.join(flow))
                        .map(t -> t.first())
                        .runWith(Sink.seq(), mat);
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), timeoutFailure);
        List<Try<String>> actual = result.toCompletableFuture().get();
        Assert.assertEquals(3, actual.size());
        Assert.assertTrue(actual.containsAll(expected));
    }

    @Test
    public void testWithCleanUp() throws ExecutionException, InterruptedException {

        final AtomicInteger counter = new AtomicInteger(0);

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, UUID>)elem);

        Consumer<String> cleanUp = s -> counter.incrementAndGet();
        TimeoutSettings settings = TimeoutSettings.<String, String, UUID>create(Duration.ofSeconds(Timing.timeout().toSeconds())).withCleanUp(cleanUp);
        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> timeoutBidiFlow =
                Timeout.create(settings);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, UUID.randomUUID()))
                        .via(timeoutBidiFlow.join(flow))
                        .map(t -> t.first())
                        .runWith(Sink.seq(), mat);
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), timeoutFailure);
        List<Try<String>> actual = result.toCompletableFuture().get();
        Assert.assertEquals(3, actual.size());
        Assert.assertTrue(actual.containsAll(expected));
        Assert.assertEquals(1, counter.get());
    }

    @Test
    public void testWithUniqueIdMapperAndCleanUp() throws ExecutionException, InterruptedException {

        final AtomicInteger counter = new AtomicInteger(0);

        class MyContext {
            private String s;
            private UUID uuid;

            public MyContext(String s, UUID uuid) {
                this.s = s;
                this.uuid = uuid;
            }
        }

        final ActorRef delayActor = system.actorOf(Props.create(DelayActor.class));
        final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
                Flow.<Pair<String, MyContext>>create()
                        .mapAsyncUnordered(3, elem -> ask(delayActor, elem, Duration.ofSeconds(5)))
                        .map(elem -> (Pair<String, MyContext>)elem);

        Consumer<String> cleanUp = s -> counter.incrementAndGet();
        TimeoutSettings settings = TimeoutSettings.<String, String, MyContext>create(Duration.ofSeconds(Timing.timeout().toSeconds()))
                .withUniqueIdMapper(func(context -> context.uuid))
                .withCleanUp(cleanUp);
        final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> timeoutBidiFlow =
                Timeout.create(settings);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
                        .via(timeoutBidiFlow.join(flow))
                        .map(t -> t.first())
                        .runWith(Sink.seq(), mat);
        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), timeoutFailure);
        List<Try<String>> actual = result.toCompletableFuture().get();
        Assert.assertEquals(3, actual.size());
        Assert.assertTrue(actual.containsAll(expected));
        Assert.assertEquals(1, counter.get());
    }
}

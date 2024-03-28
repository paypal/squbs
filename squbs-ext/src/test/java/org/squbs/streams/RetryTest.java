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
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.BidiFlow;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.junit.Test;
import org.squbs.metrics.MetricsExtension;
import scala.concurrent.duration.Duration;
import scala.util.Failure;
import scala.util.Success;
import scala.util.Try;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static scala.compat.java8.JFunction.*;

public class RetryTest {

    final private ActorSystem system = ActorSystem.create("RetryTest");
    final private Materializer mat = ActorMaterializer.create(system);
    final private Try<String> failure = Failure.apply(new Exception("failed"));

    final class MyContext {
        private String s;
        private UUID uuid;

        public MyContext(String s, UUID uuid) {
            this.s = s;
            this.uuid = uuid;
        }
    }

    @Test
    public void testRetryBidi() throws Exception {

        final Flow<Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .map(elem -> new Pair<>(Success.apply(elem.first()), elem.second()));

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, Pair<Try<String>, UUID>,
                NotUsed> retry = Retry.create(2);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, UUID.randomUUID()))
                        .via(retry.join(flow))
                        .map(Pair::first)
                        .runWith(Sink.seq(), mat);

        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("b"), Success.apply("c"));
        List<Try<String>> actual = result.toCompletableFuture().get();
        assertEquals(3, actual.size());
        assertTrue("Did not get the expected elements from retry stage", actual.containsAll(expected));
    }

    @Test
    public void testRetryBidiWithFailures() throws Exception {

        final Flow<Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> bottom =
                Flow.<Pair<String, UUID>>create()
                        .map(elem -> {
                            if (elem.first().equals("a") || elem.first().equals("c"))
                                return new Pair<>(failure, elem.second());
                            else
                                return new Pair<>(Success.apply(elem.first()), elem.second());
                        });
        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>,
                Pair<Try<String>, UUID>, NotUsed> retry = Retry.create(2);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, UUID.randomUUID()))
                        .via(retry.join(bottom))
                        .map(Pair::first)
                        .runWith(Sink.seq(), mat);

        final List<Try<String>> expected = Arrays.asList(failure, Success.apply("b"), failure);
        List<Try<String>> actual = result.toCompletableFuture().get();
        assertEquals(3, actual.size());
        assertTrue("Did not get the expected elements from retry stage", actual.containsAll(expected));
    }

    @Test
    public void testRetryBidiWithRetryFlow() throws Exception {

        final Flow<Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> flow =
                Flow.<Pair<String, UUID>>create()
                        .map(elem -> new Pair<>(Success.apply(elem.first()), elem.second()));

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, Pair<Try<String>, UUID>,
                NotUsed> retry = Retry.create(3);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, UUID.randomUUID()))
                        .via(retry.join(flow))
                        .map(Pair::first)
                        .runWith(Sink.seq(), mat);

        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("b"), Success.apply("c"));
        List<Try<String>> actual = result.toCompletableFuture().get();
        assertEquals(3, actual.size());
        assertTrue("Did not get the expected elements from retry stage", actual.containsAll(expected));
    }

    @Test
    public void testRetryBidiWithUniqueIdMapper() throws Exception {
        final Flow<Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> bottom =
                Flow.<Pair<String, MyContext>>create()
            .map(elem -> {
                if (elem.first().equals("b"))
                    return new Pair<>(failure, elem.second());
                else
                    return new Pair<>(Success.apply(elem.first()), elem.second());
            });

        RetrySettings settings = RetrySettings.<String, String, MyContext>create(3)
                .withUniqueIdMapper(func(context -> context.uuid));
        final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>,
                Pair<Try<String>, MyContext>, NotUsed> retryFlow =
                Retry.create(settings);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
                        .via(retryFlow.join(bottom))
                        .map(Pair::first)
                        .runWith(Sink.seq(), mat);

        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), failure);
        assertTrue("Did not get the expected elements from retry stage",
            result.toCompletableFuture().get().containsAll(expected));
    }

    @Test
    public void testRetryBidiWithFailureDecider() {
        final Flow<Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> bottom =
                Flow.<Pair<String, UUID>>create()
                        .map(elem -> new Pair<>(Success.apply(elem.first()), elem.second()));

        final Function<Try<String>, Boolean> failureDecider =
                out -> out.isFailure() || out.equals(Success.apply("a")); // treat "a" as a failure for retry

        RetrySettings settings = RetrySettings.<String, String, UUID>create(1).withFailureDecider(failureDecider);
        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>,
                Pair<Try<String>, UUID>, NotUsed> retry = Retry.create(settings);

        Source.from(Arrays.asList("a", "b"))
                .map(s -> new Pair<>(s, UUID.randomUUID()))
                .via(retry.join(bottom))
                .map(Pair::first)
                .runWith(TestSink.probe(system), mat)
                .request(2)
                .expectNext(Success.apply("a"), Success.apply("b"));
    }

    @Test
    public void testRetryBidiWithDelay() throws Exception {
        final Flow<Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> bottom = Flow.<Pair<String, UUID>>create()
                .map(elem -> {
                    if (elem.first().equals("b"))
                        return new Pair<>(failure, elem.second());
                    else
                        return new Pair<>(Success.apply(elem.first()), elem.second());
                });

        final RetrySettings retrySettings =
                RetrySettings.<String, Try<String>, UUID>create(3)
                        .withDelay(Duration.create(1, TimeUnit.SECONDS));

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>,
                Pair<Try<String>, UUID>, NotUsed> retry = Retry.create(retrySettings);

        Source.from(Arrays.asList("a", "b", "c"))
                .map(s -> new Pair<>(s, UUID.randomUUID()))
                .via(retry.join(bottom))
                .map(Pair::first)
                .runWith(TestSink.probe(system), mat)
                .request(3)
                .expectNext(Success.apply("a"), Success.apply("c"))
                .expectNoMessage(Duration.create(3, TimeUnit.SECONDS))
                .expectNext(failure);
    }

    @Test
    public void testRetryBidiWithBackoffAndMaxDelay() throws Exception {
        final Flow<Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> bottom = Flow.<Pair<String, UUID>>create()
                .map(elem -> {
                    if (elem.first().equals("b"))
                        return new Pair<>(failure, elem.second());
                    else
                        return new Pair<>(Success.apply(elem.first()), elem.second());
                });

        final RetrySettings retrySettings = RetrySettings.<String, Try<String>, UUID>create(3)
                .withDelay(Duration.create(1, TimeUnit.SECONDS))
                .withExponentialBackoff(2.0)
                .withMaxDelay(Duration.create(2, TimeUnit.SECONDS));

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>,
                Pair<Try<String>, UUID>, NotUsed> retry = Retry.create(retrySettings);

        Source.from(Arrays.asList("a", "b", "c"))
                .map(s -> new Pair<>(s, UUID.randomUUID()))
                .via(retry.join(bottom))
                .map(t -> t.first())
                .runWith(TestSink.probe(system), mat)
                .request(3)
                .expectNext(Success.apply("a"), Success.apply("c"))
                .expectNoMessage(Duration.create(5, TimeUnit.SECONDS)) //1s + 2s + 2s
                .expectNext(failure);
    }

    @Test
    public void testRetryBidiWithMapperDecider() throws Exception {

        final Flow<Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> bottom =
                Flow.<Pair<String, MyContext>>create()
                        .map(elem -> {
                            if (elem.first().equals("b"))
                                return new Pair<>(failure, elem.second());
                            else
                                return new Pair<>(Success.apply(elem.first()), elem.second());
                        });

        final Function<Try<String>, Boolean> failureDecider =
                out -> out.isFailure() || out.equals(Success.apply("a")); // treat "a" as a failure for retry

        RetrySettings settings = RetrySettings.<String, String, MyContext>create(3)
                .withUniqueIdMapper(func(context -> context.uuid))
                .withFailureDecider(failureDecider);
        final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>,
                Pair<Try<String>, MyContext>, NotUsed> retryFlow = Retry.create(settings);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
                        .via(retryFlow.join(bottom))
                        .map(t -> t.first())
                        .runWith(Sink.seq(), mat);

        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), failure);
        assertTrue("Did not get the expected elements from retry stage",
            result.toCompletableFuture().get().containsAll(expected));
    }

    @Test
    public void testRetryBidiWithSettingsMapperDecider() throws Exception {
        final Flow<Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> bottom =
                Flow.<Pair<String, MyContext>>create()
                        .map(elem -> {
                            if (elem.first().equals("b"))
                                return new Pair<>(failure, elem.second());
                            else
                                return new Pair<>(Success.apply(elem.first()), elem.second());
                        });

        final Function<Try<String>, Boolean> failureDecider =
                out -> out.isFailure() || out.equals(Success.apply("a")); // treat "a" as failure

        final RetrySettings<String, String, MyContext> retrySettings =
                RetrySettings.<String, String, MyContext>create(3)
                    .withFailureDecider(failureDecider)
                    .withUniqueIdMapper(func(context -> context.uuid));

        final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>,
                Pair<Try<String>, MyContext>, NotUsed> retryFlow =
                Retry.create(retrySettings);

        final CompletionStage<List<Try<String>>> result =
                Source.from(Arrays.asList("a", "b", "c"))
                        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
                        .via(retryFlow.join(bottom))
                        .map(Pair::first)
                        .runWith(Sink.seq(), mat);

        final List<Try<String>> expected = Arrays.asList(Success.apply("a"), Success.apply("c"), failure);
        assertTrue("Did not get the expected elements from retry stage",
            result.toCompletableFuture().get().containsAll(expected));
    }

    @Test
    public void testRetryWithMetrics() throws Exception {
        AtomicBoolean first = new AtomicBoolean(true);
        final Flow<Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> bottom =
            Flow.<Pair<String, UUID>>create()
                .map(elem -> {
                    if (elem.first().equals("a"))
                        return new Pair<>(failure, elem.second());
                    else if (elem.first().equals("b") && first.get()) {
                        first.set(false);
                        return new Pair<>(failure, elem.second());
                    } else {
                        first.set(true);
                        return new Pair<>(Success.apply(elem.first()), elem.second());
                    }
                });

        final RetrySettings retrySettings =
            RetrySettings.<String, Try<String>, UUID>create(1)
                .withMetrics("myRetry", system);

        final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>,
            Pair<Try<String>, UUID>, NotUsed> retry = Retry.create(retrySettings);

        final CompletionStage<List<Try<String>>> result = Source.from(Arrays.asList("a", "b", "c"))
            .map(s -> new Pair<>(s, UUID.randomUUID()))
            .via(retry.join(bottom))
            .map(Pair::first)
            .runWith(Sink.seq(), mat);

        final List<Try<String>> expected = Arrays.asList(failure, Success.apply("b"), Success.apply("c"));
        assertTrue("Did not get the expected elements from retry stage",
            result.toCompletableFuture().get().containsAll(expected));

        assertEquals(2, metricsJmxValue("myRetry.retry-count", "Count"));
        assertEquals(1, metricsJmxValue("myRetry.failed-count", "Count"));
        assertEquals(2, metricsJmxValue("myRetry.success-count", "Count"));
    }

    private int metricsJmxValue(String name, String key) throws Exception {
        ObjectName oName = ObjectName.getInstance(MetricsExtension.get(system).Domain() + ":name=" + name);
        return Integer.parseInt(ManagementFactory.getPlatformMBeanServer().getAttribute(oName, key).toString());
    }
}

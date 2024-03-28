package org.squbs.stream;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.ThrottleMode;
import org.apache.pekko.stream.javadsl.*;
import org.squbs.lifecycle.GracefulStop;
import org.squbs.unicomplex.Timeouts;
import org.squbs.util.DurationConverters;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ProperShutdownStreamJ extends AbstractPerpetualStream<Pair<Supplier<ActorRef>, CompletionStage<Long>>> {

    static final AtomicLong genCount = new AtomicLong(0L);


    Source<Integer, NotUsed> toBeManaged =
            Source.unfold(0, i -> {
                if (i == Integer.MAX_VALUE) {
                    genCount.incrementAndGet();
                    return Optional.of(Pair.create(0, i));
                } else {
                    genCount.incrementAndGet();
                    return Optional.of(Pair.create(i + 1, i));
                }
            });

    Source<Integer, Pair<NotUsed, Supplier<ActorRef>>> managedSource =
            new LifecycleManaged<Integer, NotUsed>()
                    .source(toBeManaged);

    Sink<Integer, CompletionStage<Long>> counter =
            Flow.<Integer>create()
                    .map(l -> 1L)
                    .reduce((i, j) -> i + j)
                    .toMat(Sink.head(), Keep.right());

    @Override
    public long getStopTimeout() {
        return Timeouts.awaitMax().toMillis();
    }

    @Override
    public RunnableGraph<Pair<Supplier<ActorRef>, CompletionStage<Long>>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(managedSource, counter, (a, b) -> Pair.create(a.second(), b),
                (builder, source, sink) -> {
                    FlowShape<Integer, Integer> throttle = builder.add(Flow.<Integer>create().throttle(5000, Duration.ofSeconds(1), 1000,
                            ThrottleMode.shaping()));
                    builder.from(source).via(throttle).to(sink);
                    return ClosedShape.getInstance();
                }));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(NotifyWhenDone.getInstance(), n -> {
                    CompletionStage<Long> fCount = matValue().second();
                    getSender().tell(fCount, getSelf());
                })
                .build();
    }

    @Override
    public CompletionStage<Done> shutdown() {
        super.shutdown();
        Supplier<ActorRef> actorRefSupplier = matValue().first();
        CompletionStage<Long> fCount = matValue().second();
        CompletionStage<Boolean> fStopped = gracefulStop(actorRefSupplier.get(),
                DurationConverters.toJava(Timeouts.awaitMax()), GracefulStop.getInstance());
        return fCount.thenCombine(fStopped, (count, stopped) -> Done.getInstance());
    }
}
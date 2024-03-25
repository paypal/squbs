package org.squbs.stream;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.SourceShape;
import org.apache.pekko.stream.ThrottleMode;
import org.apache.pekko.stream.javadsl.*;
import org.squbs.unicomplex.Timeouts;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public class KillSwitchStreamJ extends AbstractPerpetualStream<CompletionStage<Long>> {

    static final AtomicLong genCount = new AtomicLong(0L);

    Sink<Integer, CompletionStage<Long>> counter = Flow.<Integer>create()
            .map(l -> 1L)
            .reduce((i, j) -> i + j)
            .toMat(Sink.head(), Keep.right());

    @Override
    public long getStopTimeout() {
        return Timeouts.awaitMax().toMillis();
    }

    @Override
    public RunnableGraph<CompletionStage<Long>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(counter, (builder, sink) -> {
            SourceShape<Integer> source = builder.add(
                    Source.unfold(0, i -> {
                        if (i == Integer.MAX_VALUE) {
                            genCount.incrementAndGet();
                            return Optional.of(Pair.create(0, i));
                        } else {
                            genCount.incrementAndGet();
                            return Optional.of(Pair.create(i + 1, i));
                        }
                    })
            );

            FlowShape<Integer, Integer> throttle = builder.add(Flow.<Integer>create().throttle(5000,
                    Duration.ofSeconds(1), 1000,
                    ThrottleMode.shaping()));

            FlowShape<Integer, Integer> killSwitch = builder.add(killSwitch().<Integer>flow());

            builder.from(source).via(killSwitch).via(throttle).to(sink);

            return ClosedShape.getInstance();
        }));
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .matchEquals(NotifyWhenDone.getInstance(), n -> getSender().tell(matValue(), getSelf()))
                .build();
    }
}
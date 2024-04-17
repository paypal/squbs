package org.squbs.stream;

import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.*;
import org.apache.pekko.stream.javadsl.*;
import org.squbs.unicomplex.Timeouts;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class KillSwitchMatStreamJ extends AbstractPerpetualStream<Pair<KillSwitch, CompletionStage<Long>>> {

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
    public RunnableGraph<Pair<KillSwitch, CompletionStage<Long>>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(KillSwitches.<Integer>single(), counter, Pair::create,
                (builder, kill, sink) -> {
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
                    FiniteDuration.create(1, TimeUnit.SECONDS), 1000,
                    ThrottleMode.shaping()));

            builder.from(source).via(kill).via(throttle).to(sink);

            return ClosedShape.getInstance();
        }));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(NotifyWhenDone.getInstance(), n -> getSender().tell(matValue().second(), getSelf()))
                .build();
    }
}
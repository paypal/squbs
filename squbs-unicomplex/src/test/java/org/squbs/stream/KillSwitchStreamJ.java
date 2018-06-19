package org.squbs.stream;

import akka.actor.AbstractActor;
import akka.actor.ActorRefFactory;
import akka.japi.Pair;
import akka.stream.ClosedShape;
import akka.stream.FlowShape;
import akka.stream.SourceShape;
import akka.stream.ThrottleMode;
import akka.stream.javadsl.*;
import org.squbs.unicomplex.Timeouts;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
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
                    FiniteDuration.create(1, TimeUnit.SECONDS), 1000,
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

    /**
     * Static method for testing the Java API for obtaining the materialized value of a Perpetual Stream
     * @param path The actor path of the PerpetualStream
     * @param refFactory The actor ref factory
     * @return The materialized value
     */
    public static Object getMatValue(String path, ActorRefFactory refFactory) {
        return AbstractPerpetualStream.getMatValue(path, refFactory);
    }
}
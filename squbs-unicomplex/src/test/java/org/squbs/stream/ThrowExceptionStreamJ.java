package org.squbs.stream;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.SourceShape;
import org.apache.pekko.stream.javadsl.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

public class ThrowExceptionStreamJ extends AbstractPerpetualStream<CompletionStage<Integer>> {

    static final int limit = 50000;
    static final int exceptionAt = limit * 3 / 10;
    static final AtomicInteger recordCount = new AtomicInteger(0);

    private Flow<Integer, Integer, NotUsed> injectError = Flow.<Integer>create()
            .map(n -> {
                if (n == exceptionAt) {
                    throw new NumberFormatException("This is a fake exception");
                } else {
                    return n;
                }
            });

    private Sink<Integer, CompletionStage<Integer>> counter = Flow.<Integer>create()
            .map(n -> {
                recordCount.incrementAndGet();
                return 1;
            })
            .reduce((a, b) -> a + b)
            .toMat(Sink.head(), Keep.right());

    @Override
    public RunnableGraph<CompletionStage<Integer>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(counter, (builder, sink) -> {
            SourceShape<Integer> src = builder.add(Source.range(1, limit));
            FlowShape<Integer, Integer> error = builder.add(injectError);
            builder.from(src).via(error).to(sink);
            return ClosedShape.getInstance();
        }));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(NotifyWhenDone.getInstance(), n -> {
                    ActorRef target = getSender();
                    ActorRef me = getSelf();
                    matValue().whenCompleteAsync((v, t) -> target.tell(v, me));
                })
                .build();
    }

    @Override
    public CompletionStage<Done> shutdown() {
        System.out.println("Neo Stream Result " + recordCount.get() + "\n\n");
        return CompletableFuture.completedFuture(Done.getInstance());
    }
}

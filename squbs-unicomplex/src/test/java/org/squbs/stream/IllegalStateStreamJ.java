package org.squbs.stream;

import org.apache.pekko.Done;
import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.SourceShape;
import org.apache.pekko.stream.javadsl.GraphDSL;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.squbs.unicomplex.Initialized;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class IllegalStateStreamJ extends AbstractPerpetualStream<CompletionStage<Integer>> {

    public IllegalStateStreamJ() {
        Initialized init;
        try {
            init = Initialized.success(matValue().toCompletableFuture().get().toString());
        } catch (Throwable e) {
            init = Initialized.failed(e);
        }
        getContext().getParent().tell(init, getSelf());
    }

    @Override
    public RunnableGraph<CompletionStage<Integer>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(endSink(), (builder, sink) -> {
            SourceShape<Integer> src = builder.add(startSource());
            builder.from(src).to(sink);
            return ClosedShape.getInstance();
        }));
    }

    private Source<Integer, NotUsed> startSource() {
        return Source.range(1, 10).map(i -> i * 2);
    }

    private Sink<Integer, CompletionStage<Integer>> endSink() {
        return Sink.fold(0, (a, b) -> a + b);
    }

    public CompletionStage<Done> shutdown() {
        String result;
        try {
            result = matValue().toCompletableFuture().get().toString();
        } catch (InterruptedException | ExecutionException e) {
            result = e.toString();
        }

        System.out.print("Neo Stream Result " + result + "\n\n");
        return CompletableFuture.completedFuture(Done.getInstance());
    }
}

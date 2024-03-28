package org.squbs.stream.javasamples;

import org.apache.pekko.Done;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.CompletionStrategy;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.GraphDSL;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.squbs.stream.AbstractPerpetualStream;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class MsgReceivingStream extends AbstractPerpetualStream<Pair<ActorRef, CompletionStage<Done>>> {

    Source<MyStreamMsg, ActorRef> actorSource = Source.actorRef(
            elem -> {
                if (Done.done() == elem) return Optional.of(CompletionStrategy.immediately());
                else return Optional.empty();
            },
            elem -> Optional.empty(),
            10,
            OverflowStrategy.dropHead()
    );
            // Source.actorPublisher(Props.create(MyPublisher.class));

    Sink<MyStreamMsg, CompletionStage<Done>> ignoreSink = Sink.ignore();

    @Override
    public RunnableGraph<Pair<ActorRef, CompletionStage<Done>>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(actorSource, ignoreSink, Pair::create,
                (builder, source, sink) -> {

                    builder.from(source).to(sink);

                    return ClosedShape.getInstance();
                }));
    }

    // Just forward the message to the stream source
    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(MyStreamMsg.class, msg -> {
                    ActorRef sourceActorRef = matValue().first();
                    sourceActorRef.forward(msg, getContext());
                })
                .build();
    }

    @Override
    public CompletionStage<Done> shutdown() {
        ActorRef sourceActorRef = matValue().first();
        sourceActorRef.tell("cancelStream" ,getSelf());
        return super.shutdown();
    }
}

class MyStreamMsg {}

class MyPublisher extends AbstractActor {

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create().build();
    }
}

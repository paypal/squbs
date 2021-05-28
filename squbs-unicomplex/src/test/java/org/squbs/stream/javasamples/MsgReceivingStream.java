package org.squbs.stream.javasamples;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ClosedShape;
import akka.stream.CompletionStrategy;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
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

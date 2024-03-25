package org.squbs.stream;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.stream.javadsl.MergeHub;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.squbs.unicomplex.Initializing;
import org.squbs.unicomplex.LifecycleState;

public class PerpetualStreamWithMergeHubJ extends AbstractPerpetualStream<Sink<MyMessage, NotUsed>> {

    Source<MyMessage, Sink<MyMessage, NotUsed>> source = MergeHub.of(MyMessage.class);

    ActorRef myMessageStorageActor = context().actorOf(Props.create(MyMessageStorageActor.class));

    @Override
    public LifecycleState streamRunLifecycleState() {
        return Initializing.instance();
    }

    /**
     * Describe your graph by implementing streamGraph
     *
     * @return The graph.
     */
    @Override
    public RunnableGraph<Sink<MyMessage, NotUsed>> streamGraph() {
        return source.to(Sink.actorRef(myMessageStorageActor, "Done"));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals(RetrieveMyMessageStorageActorRef.instance(),
                        ref -> getSender().tell(myMessageStorageActor, getSelf()))
                .build();
    }
}
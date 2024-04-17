package org.squbs.stream.javasamples;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.ClosedShape;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.SinkShape;
import org.apache.pekko.stream.javadsl.*;
import org.squbs.stream.AbstractPerpetualStream;
import org.squbs.unicomplex.Initializing;
import org.squbs.unicomplex.LifecycleState;

import java.util.ArrayList;
import java.util.List;

public class PerpetualStreamWithMergeHub extends AbstractPerpetualStream<Sink<MyMessage, NotUsed>> {

    // inlet - destination for MyMessage messages
    Source<MyMessage, Sink<MyMessage, NotUsed>> source = MergeHub.of(MyMessage.class);

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
        return RunnableGraph.fromGraph(GraphDSL.create(source, (builder, input) -> {

            FlowShape<MyMessage, MyMessage> killSwitch = builder.add(killSwitch().<MyMessage>flow());

            //flow component, which supposedly does something to MyMessage
            FlowShape<MyMessage, MyMessageEnrich> preProcess = builder.add(Flow.<MyMessage>create().map(inMsg -> {
                MyMessageEnrich outMsg = new MyMessageEnrich(inMsg.ip, inMsg.ts, new ArrayList<>());
                System.out.println("Message inside stream=" + inMsg);
                return outMsg;
            }));

            // building a flow based on another flow, to do some dummy enrichment
            FlowShape<MyMessageEnrich, MyMessageEnrich> enrichment =
                    builder.add(Flow.<MyMessageEnrich>create().map(inMsg -> {
                        inMsg.enrichTs.add(System.currentTimeMillis());
                        MyMessageEnrich outMsg = new MyMessageEnrich(inMsg.ip.replaceAll("\\.","-"),
                                inMsg.ts, inMsg.enrichTs);
                        System.out.println("Enriched Message inside enrich step=" + outMsg);
                        return outMsg;
                    }));

            //outlet - discard messages
            SinkShape<Object> sink = builder.add(Sink.ignore());

            builder.from(input).via(killSwitch).via(preProcess).via(enrichment).to(sink);

            return ClosedShape.getInstance();
        }));
    }
}

class MyMessage {
    public final String ip;
    public final Long ts;

    public MyMessage(String ip, long ts) {
        this.ip = ip;
        this.ts = ts;
    }
}

class MyMessageEnrich {
    public final String ip;
    public final long ts;
    public final List<Long> enrichTs;

    public MyMessageEnrich(String ip, long ts, List<Long> enrichTs) {
        this.ip = ip;
        this.ts = ts;
        this.enrichTs = enrichTs;
    }
}
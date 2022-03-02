package org.squbs.stream.javasamples;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import org.squbs.marshallers.MarshalUnmarshal;
import org.squbs.stream.FlowToPerpetualStream;

import static org.squbs.marshallers.json.JacksonMapperSupport.unmarshaller;

class HttpFlowWithMergeHub extends FlowToPerpetualStream {

    private final Materializer mat = Materializer.createMaterializer(context());
    private final MarshalUnmarshal mu = new MarshalUnmarshal(context().system().dispatcher(), mat);

    @Override
    public Flow<HttpRequest, HttpResponse, NotUsed> flow() {
        return Flow.<HttpRequest>create()
                .mapAsync(1, req -> mu.apply(unmarshaller(MyMessage.class), req.entity()))
                .alsoTo(matValue("/user/mycube/perpetualStreamWithMergeHub"))
                .map(myMessage -> HttpResponse.create().withEntity("Received Id: " + myMessage.ip));
    }
}

package org.squbs.stream.javasamples;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
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

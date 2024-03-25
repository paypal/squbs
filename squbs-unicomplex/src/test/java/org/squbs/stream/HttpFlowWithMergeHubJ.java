package org.squbs.stream;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.squbs.marshallers.MarshalUnmarshal;

public class HttpFlowWithMergeHubJ extends FlowToPerpetualStream {

    private final Materializer mat = Materializer.createMaterializer(context());
    private final MarshalUnmarshal mu = new MarshalUnmarshal(context().system().dispatcher(), mat);

    private final Unmarshaller<RequestEntity, MyMessage> unmarshaller = Unmarshaller.async(entity ->
            entity.toStrict(1000, mat).thenApply(e ->
                    new MyMessage(Integer.parseInt(e.getData().utf8String()))
            )
    );

    @Override
    public Flow<HttpRequest, HttpResponse, NotUsed> flow() {
        return Flow.<HttpRequest>create()
                .mapAsync(1, req -> mu.<RequestEntity, MyMessage>apply(unmarshaller, req.entity()))
                .alsoTo(matValue("/user/JavaPerpetualStreamMergeHubSpec/perpetualStreamWithMergeHub"))
                .map(myMessage -> HttpResponse.create().withEntity("Received Id: " + myMessage.id()));
    }
}

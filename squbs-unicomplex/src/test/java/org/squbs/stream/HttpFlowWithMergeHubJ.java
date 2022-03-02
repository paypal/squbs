package org.squbs.stream;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
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

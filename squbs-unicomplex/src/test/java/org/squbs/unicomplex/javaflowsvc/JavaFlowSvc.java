/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.squbs.unicomplex.javaflowsvc;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;
import org.squbs.unicomplex.AbstractFlowDefinition;
import scala.Tuple2;

public class JavaFlowSvc extends AbstractFlowDefinition {

    private final String pingPath;
    private final String chunkPath;
    private final String exceptionPath;

    public JavaFlowSvc() {
        String prefix = "";
        if (!webContext().isEmpty()) {
            prefix = '/' + webContext();
        }
        pingPath = prefix + "/ping";
        chunkPath = prefix + "/chunks";
        exceptionPath = prefix + "/throwit";
    }

    @Override
    public Flow<HttpRequest, HttpResponse, NotUsed> flow() {
        return Flow.of(HttpRequest.class)
                .map(req -> {
                    String path = req.getUri().path();
                    if (pingPath.equals(path)) {
                        return HttpResponse.create().withStatus(StatusCodes.OK).withEntity("pong");
                    } else if (chunkPath.equals(path)) {
                        Source<ByteString, Object> responseChunks = req.entity().getDataBytes()
                                .filter(ByteString::nonEmpty)
                                .map(b -> new Tuple2<>(1, b.length()))
                                .reduce((a, b) -> new Tuple2<>(a._1() + b._1(), a._2() + b._2()))
                                .map(cb -> ByteString.fromString(
                                        "Received " + cb._1() + " chunks and " + cb._2() + " bytes.\r\n"))
                                .concat(Source.single(ByteString.fromString("This is the last chunk!")));
                        return HttpResponse.create()
                                .withStatus(StatusCodes.OK)
                                .withEntity(HttpEntities.createChunked(ContentTypes.TEXT_PLAIN_UTF8, responseChunks));
                    } else if (exceptionPath.equals(path)) {
                        throw new IllegalArgumentException("This path is supposed to throw this exception!");
                    } else {
                        return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("Path not found!");
                    }
                });
    }
}

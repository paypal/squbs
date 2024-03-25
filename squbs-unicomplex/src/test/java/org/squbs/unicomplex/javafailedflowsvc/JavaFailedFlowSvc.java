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
package org.squbs.unicomplex.javafailedflowsvc;

import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.stream.javadsl.Flow;
import org.squbs.unicomplex.AbstractFlowDefinition;

/**
 * A Flow definition that will always fail to initialize. Remember, it should use only no-arg constructors.
 */
public class JavaFailedFlowSvc extends AbstractFlowDefinition {

    private final String pingPath;

    public JavaFailedFlowSvc(String name) {
        String prefix = "";
        if (!webContext().isEmpty()) {
            prefix = '/' + webContext();
        }
        pingPath = prefix + "/ping/" + name;
    }

    @Override
    public Flow<HttpRequest, HttpResponse, NotUsed> flow() {
        return Flow.of(HttpRequest.class)
                .map(req -> {
                    String path = req.getUri().path();
                    if (pingPath.equals(path)) {
                        return HttpResponse.create().withStatus(StatusCodes.OK).withEntity("pong");
                    } else {
                        return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("Path not found!");
                    }
                });
    }
}

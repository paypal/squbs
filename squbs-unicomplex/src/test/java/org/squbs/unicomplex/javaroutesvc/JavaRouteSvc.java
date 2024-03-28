/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.squbs.unicomplex.javaroutesvc;


import org.apache.pekko.http.javadsl.server.CustomRejection;
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.RejectionHandler;
import org.apache.pekko.http.javadsl.server.Route;
import org.squbs.unicomplex.AbstractRouteDefinition;

import java.util.Optional;

public class JavaRouteSvc extends AbstractRouteDefinition {
    @Override
    public Optional<RejectionHandler> rejectionHandler() {
        return Optional.of(RejectionHandler.newBuilder()
                .handle(ServiceRejection.class, sr ->
                        complete("rejected"))
                .build());
    }

    @Override
    public Optional<ExceptionHandler> exceptionHandler() {
        return Optional.of(ExceptionHandler.newBuilder()
                .match(ServiceException.class, se ->
                        complete("exception"))
                .build());
    }

    @Override
    public Route route() {
        return route(
                path("ping", () ->
                        complete("pong")
                ),
                path("reject", () ->
                        reject(new ServiceRejection())
                ),
                path("exception", () ->
                        failWith(new ServiceException())
                )
        );
    }
}

class ServiceRejection implements CustomRejection { }

class ServiceException extends Exception { }

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
package org.squbs.testkit.japi;

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.server.Route;
import org.squbs.unicomplex.AbstractRouteDefinition;

public class InfoRoute extends AbstractRouteDefinition {

    @Override
    public Route route() {
        return get(() ->
                path("context-info", () ->
                        complete(HttpEntities.create(ContentTypes.APPLICATION_JSON,
                                "{ \"webContext\": \"" + webContext() +
                                        "\" , \"actorPath\": \"" + context().self().path() + "\" }"))
                )
        );
    }
}

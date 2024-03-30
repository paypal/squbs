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
package org.squbs.testkit.japi.testng;

import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.squbs.testkit.japi.InfoRouteWithActor;
import org.squbs.testkit.japi.RouteResultInfo;
import org.squbs.testkit.japi.TestNGCustomRouteTestKit;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CustomRouteTestKitWithClassPathTest extends TestNGCustomRouteTestKit {

    public CustomRouteTestKitWithClassPathTest() {
        super(true);
    }

    @Test
    public void testSimpleRoute() {
        TestRoute simpleRoute = testRoute(InfoRouteWithActor.class);
        RouteResultInfo routeInfo = simpleRoute.run(HttpRequest.GET("/increment/10"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteResultInfo.class));
        assertEquals(routeInfo.getWebContext(), "");
        assertTrue(routeInfo.getActorPath().startsWith("pekko://"),
                "ActorPath: " + routeInfo.getActorPath() + " does not start with pekko://");
        assertEquals(routeInfo.getResult(), 11);
    }

}


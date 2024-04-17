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
package org.squbs.testkit.japi.testng;

import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.server.Rejections;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.squbs.testkit.japi.InfoRoute;
import org.squbs.testkit.japi.RouteInfo;
import org.squbs.testkit.japi.TestNGRouteTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RouteTestTest extends TestNGRouteTest {

    @Test
    public void testSimpleRoute() {
        TestRoute simpleRoute = testRoute(InfoRoute.class);
        RouteInfo routeInfo = simpleRoute.run(HttpRequest.GET("/context-info"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteInfo.class));
        assertEquals(routeInfo.getWebContext(), "");
        assertTrue(routeInfo.getActorPath().startsWith("pekko://"),
                "ActorPath: " + routeInfo.getActorPath() + " does not start with pekko://");
    }

    @Test
    public void testSimpleRouteWithContext() {
        TestRoute simpleRoute = testRoute("my-context", InfoRoute.class);
        RouteInfo routeInfo = simpleRoute.run(HttpRequest.GET("/my-context/context-info"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteInfo.class));
        assertEquals(routeInfo.getWebContext(), "my-context");
        assertTrue(routeInfo.getActorPath().startsWith("pekko://"),
                "ActorPath: " + routeInfo.getActorPath() + " does not start with pekko://");
    }

    @Test
    public void testSimpleRouteWithRejections() {
        TestRoute simpleRoute = testRoute(InfoRoute.class);
        simpleRoute.runWithRejections(HttpRequest.POST("context-info"))
                .assertRejections(Rejections.method(HttpMethods.GET));
    }

    @Test
    public void testSimpleRouteWithContextWithRejections() {
        TestRoute simpleRoute = testRoute("my-context", InfoRoute.class);
        simpleRoute.runWithRejections(HttpRequest.POST("/my-context/context-info"))
                .assertRejections(Rejections.method(HttpMethods.GET));
    }
}

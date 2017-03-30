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
package org.squbs.testkit.japi.junit;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.testkit.TestRoute;
import org.junit.Test;
import org.squbs.testkit.japi.InfoRoute;
import org.squbs.testkit.japi.JUnitRouteTest;
import org.squbs.testkit.japi.RouteInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RouteTestTest extends JUnitRouteTest {

    @Test
    public void testSimpleRoute() {
        TestRoute simpleRoute = testRoute(InfoRoute.class);
        RouteInfo routeInfo = simpleRoute.run(HttpRequest.GET("/context-info"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteInfo.class));
        assertEquals("", routeInfo.getWebContext());
        assertTrue("ActorPath: " + routeInfo.getActorPath() + " does not start with akka://",
                routeInfo.getActorPath().startsWith("akka://"));
    }

    @Test
    public void testSimpleRouteWithContext() {
        TestRoute simpleRoute = testRoute("my-context", InfoRoute.class);
        RouteInfo routeInfo = simpleRoute.run(HttpRequest.GET("/my-context/context-info"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteInfo.class));
        assertEquals("my-context", routeInfo.getWebContext());
        assertTrue("ActorPath: " + routeInfo.getActorPath() + " does not start with akka://",
                routeInfo.getActorPath().startsWith("akka://"));
    }
}


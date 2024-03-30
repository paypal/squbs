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

import org.apache.pekko.http.javadsl.marshallers.jackson.Jackson;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;
import org.squbs.testkit.japi.InfoRouteWithActor;
import org.squbs.testkit.japi.RouteResultInfo;
import org.squbs.testkit.japi.JUnitCustomRouteTestKit;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CustomRouteTestKitResourcesTest extends JUnitCustomRouteTestKit {

    static List<String> resources = Arrays.asList(CustomRouteTestKitResourcesTest.class.getClassLoader()
            .getResource("").getPath() + "/CustomTestKitDefaultSpec/META-INF/squbs-meta.conf");

    public CustomRouteTestKitResourcesTest() {
        super(resources, false);
    }

    @Test
    public void testSimpleRoute() {
        TestRoute simpleRoute = testRoute(InfoRouteWithActor.class);
        RouteResultInfo routeInfo = simpleRoute.run(HttpRequest.GET("/decrement/10"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteResultInfo.class));
        assertEquals("", routeInfo.getWebContext());
        assertTrue("ActorPath: " + routeInfo.getActorPath() + " does not start with pekko://",
                routeInfo.getActorPath().startsWith("pekko://"));
        assertEquals(9, routeInfo.getResult());
    }

    @Test
    public void testSimpleRouteWithContext() {
        TestRoute simpleRoute = testRoute("my-context", InfoRouteWithActor.class);
        RouteResultInfo routeInfo = simpleRoute.run(HttpRequest.GET("/my-context/decrement/20"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteResultInfo.class));
        assertEquals("my-context", routeInfo.getWebContext());
        assertTrue("ActorPath: " + routeInfo.getActorPath() + " does not start with pekko://",
                routeInfo.getActorPath().startsWith("pekko://"));
        assertEquals(19, routeInfo.getResult());
    }
}


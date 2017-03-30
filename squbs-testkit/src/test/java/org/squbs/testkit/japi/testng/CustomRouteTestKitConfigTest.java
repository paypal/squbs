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

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.testkit.TestRoute;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.squbs.testkit.japi.InfoRouteWithActor;
import org.squbs.testkit.japi.RouteResultInfo;
import org.squbs.testkit.japi.TestNGCustomRouteTestKit;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CustomRouteTestKitConfigTest extends TestNGCustomRouteTestKit {

    static Config customConfig = ConfigFactory.parseString("CustomRouteTestKitConfigResourcesTest.foobar = baz");

    public CustomRouteTestKitConfigTest() {
       super(customConfig);
    }

    @Test
    public void testSimpleRoute() {
        TestRoute simpleRoute = testRoute(InfoRouteWithActor.class);
        RouteResultInfo routeInfo = simpleRoute.run(HttpRequest.GET("/increment/10"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteResultInfo.class));
        assertEquals(routeInfo.getWebContext(), "");
        assertTrue(routeInfo.getActorPath().startsWith("akka://"),
                "ActorPath: " + routeInfo.getActorPath() + " does not start with akka://");
        assertEquals(routeInfo.getResult(), 11);
        assertEquals(system().settings().config().getString("CustomRouteTestKitConfigResourcesTest.foobar"),
                "baz");
    }

    @Test
    public void testSimpleRouteWithContext() {
        TestRoute simpleRoute = testRoute("my-context", InfoRouteWithActor.class);
        RouteResultInfo routeInfo = simpleRoute.run(HttpRequest.GET("/my-context/increment/20"))
                .assertStatusCode(200)
                .entity(Jackson.unmarshaller(RouteResultInfo.class));
        assertEquals(routeInfo.getWebContext(), "my-context");
        assertTrue(routeInfo.getActorPath().startsWith("akka://"),
                "ActorPath: " + routeInfo.getActorPath() + " does not start with akka://");
        assertEquals(routeInfo.getResult(), 21);
        assertEquals(system().settings().config().getString("CustomRouteTestKitConfigResourcesTest.foobar"),
                "baz");
    }
}


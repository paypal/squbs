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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.testkit.javadsl.TestKit;
import com.typesafe.config.ConfigFactory;
import org.squbs.testkit.TestActorJ;
import org.squbs.testkit.japi.Timeouts;
import org.squbs.unicomplex.JMX;
import org.squbs.unicomplex.UnicomplexBoot;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;


public class CustomTestKitTest extends CustomTestKit {

    CustomTestKitTest() {
        super(TestConfig.boot);
    }

    @AfterClass
    public void tearDown() {
        shutdown();
    }

    @Test
    public void testRouteResponding() throws ExecutionException, InterruptedException {
        final Materializer materializer = ActorMaterializer.create(system());

        final CompletionStage<HttpResponse> responseFuture =
                Http.get(system())
                        .singleRequest(HttpRequest.create("http://127.0.0.1:" + port() + "/test"));
        Assert.assertTrue(responseFuture.toCompletableFuture().get().entity()
                .toStrict(Timeouts.awaitMax().toMillis(), materializer).toCompletableFuture().get().getData()
                .utf8String().contains("success"));
    }

    @Test
    public void testPong() {
        new TestKit(system()) {{
            final Props props = Props.create(TestActorJ.class);
            final ActorRef subject = system().actorOf(props);
            subject.tell("Ping", getRef());
            Assert.assertEquals(receiveOne(Timeouts.awaitMax()), "Pong");
        }};
    }

    private static class TestConfig {

        private static Map<String, Object> configMap = new HashMap<>();
        static {

            configMap.put("squbs.actorsystem-name", "CustomTestKitTest");
            configMap.put("default-listener.bind-port", "0");
            configMap.put("squbs." + JMX.prefixConfig(), true);
        }

        private static final List<String> resources = Arrays.asList(TestConfig.class.getClassLoader()
                .getResource("").getPath() + "/CustomTestKitTest/META-INF/squbs-meta.conf");

        private static final UnicomplexBoot boot =
                UnicomplexBoot.apply(ConfigFactory.parseMap(configMap)).scanResources(resources).start();
    }
}

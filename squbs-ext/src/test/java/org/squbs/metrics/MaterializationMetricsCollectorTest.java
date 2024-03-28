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
package org.squbs.metrics;

import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.RunnableGraph;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.testkit.TestPublisher;
import org.apache.pekko.stream.testkit.javadsl.TestSource;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletionStage;

public class MaterializationMetricsCollectorTest {

    private static final ActorSystem system = ActorSystem.create("MaterializationMetricsCollectorTest");
    private static final Materializer mat = ActorMaterializer.create(system);

    @AfterClass
    public static void afterClass() {
        system.terminate();
    }

    @Test
    public void testMaterializationMetricsCollector() throws Exception {
        final RunnableGraph<Pair<TestPublisher.Probe<Integer>, CompletionStage<Done>>> stream =
                TestSource.<Integer>probe(system)
                        .via(MaterializationMetricsCollector.create("upstream-finishes", system))
                        .toMat(Sink.ignore(), Keep.both());
        Pair<TestPublisher.Probe<Integer>, CompletionStage<Done>> mat1 = stream.run(mat);
        stream.run(mat);

        Assert.assertEquals(jmxValue("upstream-finishes-active-count", "Count"), 2);
        Assert.assertEquals(jmxValue("upstream-finishes-creation-count", "Count"), 2);

        mat1.first().sendComplete();
        mat1.second().toCompletableFuture().get();

        Assert.assertEquals(jmxValue("upstream-finishes-active-count", "Count"), 1);
        Assert.assertEquals(jmxValue("upstream-finishes-creation-count", "Count"), 2);
        Assert.assertEquals(jmxValue("upstream-finishes-termination-count", "Count"), 1);
    }

    private int jmxValue(String name, String key) throws Exception {
        ObjectName oName = ObjectName.getInstance(
                MetricsExtension.get(system).Domain()+ ":name=" + MetricsExtension.get(system).Domain() + "." + name);
        return Integer.parseInt(ManagementFactory.getPlatformMBeanServer().getAttribute(oName, key).toString());
    }
}

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
package org.squbs.stream;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;

import static org.squbs.stream.TriggerEvent.DISABLE;
import static org.squbs.stream.TriggerEvent.ENABLE;

public class TriggerMergeJ {
    private final ActorSystem system;

    private final Materializer materializer;

    public TriggerMergeJ(ActorSystem system) {
        this.system = system;
        materializer = ActorMaterializer.create(system);
    }

    public Pair<Pair<TestPublisher.Probe<String>, TestPublisher.Probe<Integer>>, TestSubscriber.Probe<String>> runnableGraph() {
        final Source<String, TestPublisher.Probe<String>> in = TestSource.<String>probe(system);
        final Source<TriggerEvent, TestPublisher.Probe<Integer>> trigger = TestSource.<Integer>probe(system).collect(new PFBuilder<Integer, TriggerEvent>()
                        .match(Integer.class, p -> p == 1, p -> ENABLE)
                        .match(Integer.class, p -> p == 0, p -> DISABLE)
                        .build()
        );

        return new Trigger<String, TestPublisher.Probe<String>, TestPublisher.Probe<Integer>>(false)
                .source(in, trigger)
                .buffer(1, OverflowStrategy.backpressure()
                ).toMat(TestSink.<String>probe(system), Keep.both()).run(materializer);
    }
}

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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.testkit.TestPublisher;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.stream.testkit.javadsl.TestSource;

import java.util.function.Supplier;

public class UnicomplexActorPublisherJ {
    private final ActorSystem system;

    private final Materializer materializer;

    public UnicomplexActorPublisherJ(ActorSystem system) {
        this.system = system;
        materializer = ActorMaterializer.create(system);
    }

    public Pair<Pair<TestPublisher.Probe<String>, Supplier<ActorRef>>, TestSubscriber.Probe<String>> runnableGraph() {
        Source<String, TestPublisher.Probe<String>> in = TestSource.<String>probe(system);
        return new LifecycleManaged<String, TestPublisher.Probe<String>>().source(in).toMat(TestSink.<String>probe(system), Keep.both()).run(materializer);
    }
}

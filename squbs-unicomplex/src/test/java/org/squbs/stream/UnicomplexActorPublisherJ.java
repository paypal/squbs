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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestPublisher;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.stream.testkit.javadsl.TestSource;

public class UnicomplexActorPublisherJ {
    private final ActorSystem system;

    private final Materializer materializer;

    public UnicomplexActorPublisherJ(ActorSystem system) {
        this.system = system;
        materializer = ActorMaterializer.create(system);
    }

    public Pair<Pair<TestPublisher.Probe<String>, ActorRef>, TestSubscriber.Probe<String>> runnableGraph() {
        Source<String, TestPublisher.Probe<String>> in = TestSource.<String>probe(system);
        return new LifecycleManaged<String, TestPublisher.Probe<String>>().source(in).toMat(TestSink.<String>probe(system), Keep.both()).run(materializer);
    }
}

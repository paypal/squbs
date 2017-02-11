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

package org.squbs.pattern.orchestration.japi;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import org.squbs.testkit.Timeouts;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.squbs.pattern.orchestration.japi.Messages.*;

public class SelectionOrchestrator extends AbstractOrchestrator {

    public SelectionOrchestrator() {
        getContext().actorOf(Props.create(ServiceEmulator.class), "ServiceEmulator");
        expectOnce(ReceiveBuilder.match(TestRequest.class,
                testRequest -> orchestrate(testRequest, sender())
        ).build());
    }

    protected void orchestrate(TestRequest request, ActorRef requester) {

        FiniteDuration delay = new FiniteDuration(10, TimeUnit.MILLISECONDS);

        long startTime = System.nanoTime();

        CompletableFuture<Long> responseF1 = loadResponse(delay);

        CompletableFuture<Long> responseF2 =
            responseF1.thenCompose(response1 ->
                loadResponse1(delay, response1));

        CompletableFuture<Long> responseF3 =
            responseF1.thenCompose(response1 ->
            responseF2.thenCompose(response2 ->
                loadResponse2(delay, response1, response2)));

        requester.tell(new SubmittedOrchestration(request.message, System.nanoTime() - startTime), self());

        responseF1.thenCompose(r1 ->
        responseF2.thenCompose(r2 ->
        responseF3.thenAccept(r3 -> {
            requester.tell(new FinishedOrchestration(r1 + r2 + r3, request.message, System.nanoTime() - startTime),
                    self());
            context().stop(self());
        })));
    }

    public CompletableFuture<Long> loadResponse(FiniteDuration delay) {

        CompletableFuture<ServiceResponse> future = new CompletableFuture<>();
        final long id = nextMessageId();
        ServiceRequest request = new ServiceRequest(id, delay);
        ask(context().actorSelection("ServiceEmulator"), request, Timeouts.askTimeout()).thenComplete(future);
        return future.thenApply(r -> r.id);
    }

    public CompletableFuture<Long> loadResponse1(FiniteDuration delay, long prevId) {
        return loadResponse(delay);
    }

    public CompletableFuture<Long> loadResponse2(FiniteDuration delay, long prevId, long prevId2) {
        return loadResponse(delay);
    }


}

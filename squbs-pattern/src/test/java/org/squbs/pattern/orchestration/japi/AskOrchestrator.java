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

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.util.Timeout;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.CompletableFuture;

import static org.squbs.pattern.orchestration.japi.Messages.*;

public class AskOrchestrator extends AbstractOrchestrator {

    protected ActorRef service = context().actorOf(Props.create(ServiceEmulator.class));

    protected final Timeout askTimeout;

    protected final FiniteDuration orchDelay;

    public AskOrchestrator(FiniteDuration orchDelay, Timeout askTimeout) {
        this.orchDelay = orchDelay;
        this.askTimeout = askTimeout;

        expectOnce(ReceiveBuilder.create().match(TestRequest.class,
                testRequest -> orchestrate(testRequest, sender())).build().onMessage());

    }

    protected void orchestrate(TestRequest request, ActorRef requester) {

        long startTime = System.nanoTime();

        final CompletableFuture<Long> responseF1 = loadResponse(orchDelay);

        final CompletableFuture<Long> responseF2 =
            responseF1.thenCompose(response1 ->
                loadResponse1(orchDelay, response1));

        final CompletableFuture<Long> responseF3 =
            responseF1.thenCompose(response1 ->
            responseF2.thenCompose(response2 ->
                loadResponse2(orchDelay, response1, response2)));

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
        ask(service, request, askTimeout).thenComplete(future);
        return future.handle((r, t) -> {
            if (r != null) {
                return r.id;
            } else {
                return -1L;
            }
        });
    }

    public CompletableFuture<Long> loadResponse1(FiniteDuration delay, long prevId) {
        return loadResponse(delay);
    }

    public CompletableFuture<Long> loadResponse2(FiniteDuration delay, long prevId, long prevId2) {
        return loadResponse(delay);
    }
}

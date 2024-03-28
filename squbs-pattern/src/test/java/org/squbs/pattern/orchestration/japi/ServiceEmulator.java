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

import org.apache.pekko.actor.AbstractActor;

import static org.squbs.pattern.orchestration.japi.Messages.*;

public class ServiceEmulator extends AbstractActor {

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(ServiceRequest.class, request ->
                getContext().system().scheduler().scheduleOnce(request.delay, sender(),
                        new ServiceResponse(request.id), getContext().dispatcher(), self())
        ).build();
    }
}


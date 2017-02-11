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

import scala.concurrent.duration.FiniteDuration;

public class Messages {

    public static class TestRequest {
        public final String message;
        public TestRequest(String message) {
            this.message = message;
        }
    }

    public static class FinishedOrchestration {
        public final long lastId;
        public final String message;
        public final long timeNs;
        public FinishedOrchestration(long lastId, String message, long timeNs) {
            this.lastId = lastId;
            this.message = message;
            this.timeNs = timeNs;
        }
    }

    public static class ServiceRequest {
        public final long id;
        public final FiniteDuration delay;

        public ServiceRequest(long id, FiniteDuration delay) {
            this.id = id;
            this.delay = delay;
        }
    }

    public static class ServiceResponse {
        public final long id;

        public ServiceResponse(long id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ServiceResponse && ((ServiceResponse) o).id == id;
        }
    }

    public static class SubmittedOrchestration {
        public final String message;
        public final long timeNs;

        public SubmittedOrchestration(String message, long timeNs) {
            this.message = message;
            this.timeNs = timeNs;
        }
    }
}

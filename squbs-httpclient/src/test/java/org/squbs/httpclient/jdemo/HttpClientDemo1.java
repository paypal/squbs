/*
 *  Copyright 2015 PayPal
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

package org.squbs.httpclient.jdemo;

import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import org.squbs.httpclient.endpoint.EndpointRegistry;
import org.squbs.httpclient.japi.HttpClientFactory;
import scala.concurrent.Future;
import spray.http.HttpResponse;
import spray.http.StatusCodes;

public class HttpClientDemo1 {

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.apply("HttpClientDemo1");
        EndpointRegistry.get(system).register(new GoogleMapAPIEndpointResolver(system));
        Future<HttpResponse> responseFuture = HttpClientFactory.get("GoogleMap", system).raw().
                get("/api/elevation/json?locations=27.988056,86.925278&sensor=false");
        responseFuture.onComplete(new OnComplete<HttpResponse>() {
            public void onComplete(Throwable failure, HttpResponse response) {
                if (failure == null) {
                    if (response.status() == StatusCodes.OK()) {
                        System.out.println("Success, response entity is: " + response.entity().asString());
                    } else {
                        System.out.println("Success, the status code is: " + response.status());
                    }
                } else {
                    System.out.println("Failure, the reason is: " + failure.getMessage());
                }
                system.shutdown();
            }
        }, system.dispatcher());
    }
}

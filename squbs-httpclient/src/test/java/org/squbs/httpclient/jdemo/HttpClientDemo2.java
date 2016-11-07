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

import java.util.Collections;
import java.util.List;

public class HttpClientDemo2 {

    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.apply("HttpClientDemo1");
        EndpointRegistry.get(system).register(new GoogleMapAPIEndpointResolver(system));
        Future<ElevationResult> responseFuture = HttpClientFactory.get("GoogleMap", system).
                get("/api/elevation/json?locations=27.988056,86.925278&sensor=false", ElevationResult.class);
        responseFuture.onComplete(new OnComplete<ElevationResult>() {
            public void onComplete(Throwable failure, ElevationResult data) {
                if (failure == null) {
                    System.out.println("Success, elevation is: " + data.getResults().get(0));
                } else {
                    System.out.println("Failure, the reason is: " + failure.getMessage());
                }
                system.shutdown();
            }
        }, system.dispatcher());
    }

    // Now we need to define all the types we use for communicating with Google Maps.

    static class Location {

        private final double lat, lng;

        public Location(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }
    }

    static class Elevation {

        private final double elevation;
        private final Location location;
        private final double resolution;

        public Elevation(double elevation, Location location, double resolution) {
            this.location = location;
            this.elevation = elevation;
            this.resolution = resolution;
        }

        public double getElevation() {
            return elevation;
        }

        public Location getLocation() {
            return location;
        }

        public double getResolution() {
            return resolution ;
        }

        @Override
        public String toString() {
            return "Elevation(lat = " + location.getLat() + ", long = " + location.getLng() +
                    ", alt = " + elevation +')';
        }
    }

    static class ElevationResult {

        private final String status;
        private final List<Elevation> results;

        public ElevationResult(String status, List<Elevation> results) {
            this.status = status;
            this.results = Collections.unmodifiableList(results);
        }

        public String getStatus() {
            return status;
        }

        public List<Elevation> getResults() {
            return results;
        }
    }
}

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
import org.squbs.httpclient.endpoint.Endpoint;
import org.squbs.httpclient.env.Environment;
import org.squbs.httpclient.japi.AbstractEndpointResolver;
import org.squbs.httpclient.japi.EndpointFactory;
import scala.Option;

public class GoogleMapAPIEndpointResolver extends AbstractEndpointResolver {

    private final ActorSystem system;

    public GoogleMapAPIEndpointResolver(ActorSystem system) {
        this.system = system;
    }

    @Override
    public String name() {
        return "GoogleMap";
    }

    @Override
    public Option<Endpoint> resolve(String svcName, Environment env) {
        Option<Endpoint> response;
        if (name().equals(svcName))
            response = Option.apply(EndpointFactory.create("http://maps.googleapis.com/maps", system));
        else
            response = Option.empty();
        return response;
    }
}

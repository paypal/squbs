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
package org.squbs.httpclient;

import org.apache.pekko.http.javadsl.model.Uri;
import org.squbs.env.Environment;
import org.squbs.resolver.AbstractResolver;

import java.util.Optional;

public class JavaEndpointResolver2 extends AbstractResolver<HttpEndpoint> {

    private final String dummyServiceEndpoint;

    public JavaEndpointResolver2(String dummyServiceEndpoint) {
        this.dummyServiceEndpoint = dummyServiceEndpoint;
    }

    @Override
    public String name() {
        return "DummyService2";
    }

    @Override
    public Optional<HttpEndpoint> resolve(String svcName, Environment env) {
        if (name().equals(svcName)) {
            return Optional.of(
                    HttpEndpoint.create(Uri.create(dummyServiceEndpoint), Optional.empty(),
                            Optional.empty(), Optional.empty()));
        } else {
            return Optional.empty();
        }
    }
}

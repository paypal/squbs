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
package org.squbs.resolver;

import org.squbs.env.DEV;
import org.squbs.env.Default;
import org.squbs.env.Environment;

import java.net.URI;
import java.util.Optional;

public class DummyLocalhostJavaResolver extends AbstractResolver<URI> {

    @Override
    public String name() {
        return "DummyLocalhostJavaResolver";
    }

    @Override
    public Optional<URI> resolve(String svcName, Environment env) {
        if (Default.value().equals(env) || DEV.value().equals(env)) {
            return Optional.of(URI.create("http://localhost:8080"));
        } else {
            return Optional.empty();
        }
    }
}

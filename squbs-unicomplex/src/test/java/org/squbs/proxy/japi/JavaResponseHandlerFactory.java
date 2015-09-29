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
package org.squbs.proxy.japi;

import akka.actor.ActorRefFactory;
import com.typesafe.config.Config;
import org.squbs.pipeline.Handler;
import org.squbs.pipeline.HandlerFactory;
import scala.Option;

public class JavaResponseHandlerFactory implements HandlerFactory {
    @Override
    public Option<Handler> create(Option<Config> config, ActorRefFactory actorRefFactory) {
        return Option.apply(new JavaResponseHandler());
    }
}

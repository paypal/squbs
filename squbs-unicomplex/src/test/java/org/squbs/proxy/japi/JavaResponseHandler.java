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
import akka.dispatch.Futures;
import org.squbs.pipeline.Handler;
import org.squbs.pipeline.RequestContext;
import scala.Tuple2;
import scala.concurrent.Future;
import spray.http.HttpHeaders;

import java.util.Arrays;

public class JavaResponseHandler implements Handler {
    @Override
    public Future<RequestContext> process(RequestContext reqCtx, ActorRefFactory context) {
        return Futures.successful(reqCtx
                .addResponseHeader(new HttpHeaders.RawHeader("JavaResponseHandler", "JavaResponseHandler"))
                .withAttributes(Arrays.asList(new Tuple2("someAttr", Arrays.asList("AttrValue")))));
        //return Future$.MODULE$.successful(reqCtx.addRequestHeader(new HttpHeaders.RawHeader("JavaRequestHandler", "JavaRequestHandler")));
    }
}

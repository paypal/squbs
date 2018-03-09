/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.pipeline;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import org.testng.annotations.Test;
import scala.Tuple2;
import scala.util.Success;
import scala.util.Try;

import java.util.*;

import static org.testng.Assert.assertEquals;

public class RequestContextTest {
    @Test
    public void testCreateWithAttributes() {
        List<String> testL = Arrays.asList("1", "2", "3");
        List<Tuple2<String, Object>> attrs = Arrays.asList(
            Tuple2.apply("keyA", "strA"),
            Tuple2.apply("keyB", testL),
            Tuple2.apply("keyC", "strC"));

        RequestContext rc = RequestContext.create(HttpRequest.create(), 0).withAttributes(attrs);
        assertEquals(rc.getAttribute("keyA"), Optional.of("strA"));
        assertEquals(rc.getAttribute("keyB"), Optional.of(testL));
        assertEquals(rc.getAttribute("keyC"), Optional.of("strC"));
        assertEquals(rc.getAttribute("keyD"), Optional.empty());
    }

    @Test
    public void testCreateFromRequest() {
        HttpRequest r = HttpRequest.create();
        RequestContext rc = RequestContext.create(r, 0);
        assertEquals(rc.getRequest(), r);
    }

    @Test
    public void testCreateFromResponse() {
        Try<HttpResponse> respT = Success.apply(HttpResponse.create());
        RequestContext rc = RequestContext.create(HttpRequest.create(), 0).withJavaResponse(respT);
        assertEquals(rc.getResponse(), Optional.of(respT));
    }

    @Test
    public void testDefaultEmptyResponse() {
        RequestContext rc = RequestContext.create(HttpRequest.create(), 0);
        assertEquals(rc.getResponse(), Optional.empty());
    }

}

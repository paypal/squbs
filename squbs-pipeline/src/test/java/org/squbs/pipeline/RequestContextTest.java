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
/*
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.scalatest.Ignore;
import org.testng.annotations.Test;
import scala.util.Success;
import scala.util.Try;

import java.util.*;

import static org.testng.Assert.assertEquals;


public class RequestContextTest {

    @Test
    public void testCreateWithAttribute() {
        List<String> testL = Arrays.asList("1", "2", "3");

        RequestContext rc = RequestContext.create(HttpRequest.create(), 0)
                .withAttribute("keyA", "strA")
                .withAttribute("keyB", testL)
                .withAttribute("keyC", "strC");
        assertEquals(rc.getAttribute("keyA"), Optional.of("strA"));
        assertEquals(rc.getAttribute("keyB"), Optional.of(testL));
        assertEquals(rc.getAttribute("keyC"), Optional.of("strC"));
        assertEquals(rc.getAttribute("keyD"), Optional.empty());
    }

    @Test
    public void testCreateWithAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("key1", "val1");
        attrs.put("key2", 1);
        attrs.put("key3", new Exception("Bad Val"));

        RequestContext rc1 = RequestContext.create(HttpRequest.create(), 0);
        RequestContext rc2 = rc1.withAttributes(attrs);

        assertEquals(rc1.attributes().size(), 0);

        assertEquals(rc2.attributes().size(), attrs.size());
        attrs.forEach((key, value) -> assertEquals(rc2.getAttribute(key), Optional.of(value)));
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
        RequestContext rc = RequestContext.create(HttpRequest.create(), 0).withResponse(respT);
        assertEquals(rc.getResponse(), Optional.of(respT));
    }

    @Test
    public void testDefaultEmptyResponse() {
        RequestContext rc = RequestContext.create(HttpRequest.create(), 0);
        assertEquals(rc.getResponse(), Optional.empty());
    }

}*/

/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.pattern.spray.japi;

import org.junit.Test;
import scala.collection.immutable.List$;
import spray.http.*;
import spray.http.parser.HttpParser;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class JApiTest {

    @Test
    public void testHttpEntity() {
        HttpEntity entity = HttpEntityFactory.create("test");
        assertEquals(entity, HttpEntity$.MODULE$.apply("test"));

        entity = HttpEntityFactory.create("abcdefg".getBytes());
        assertEquals(entity, HttpEntity$.MODULE$.apply("abcdefg".getBytes()));

        entity = HttpEntityFactory.create(ContentTypes.application$divjson(), "abc");
        assertEquals(entity, HttpEntity$.MODULE$.apply(ContentTypes.application$divjson(), "abc"));

        entity = HttpEntityFactory.create(ContentTypes.application$divjson(), "abc".getBytes());
        assertEquals(entity, HttpEntity$.MODULE$.apply(ContentTypes.application$divjson(), "abc".getBytes()));

    }

    @Test
    public void testChunkedMessageEnd() {

        ChunkedMessageEnd end = ChunkedMessageEndFactory.create();
        assertEquals(end, ChunkedMessageEnd$.MODULE$);

        end = ChunkedMessageEndFactory.create("abc");
        assertEquals(end, new ChunkedMessageEnd("abc", ChunkedMessageEnd$.MODULE$.copy$default$2()));

        end = ChunkedMessageEndFactory.create(Arrays.asList(HttpHeaderFactory.create("Content-type", "application/json")));
        assertEquals(end, ChunkedMessageEnd$.MODULE$.apply("", List$.MODULE$.empty().$colon$colon(HttpHeaderFactory.create("Content-type", "application/json"))));

        end = ChunkedMessageEndFactory.create("abc", Arrays.asList(HttpHeaderFactory.create("Content-type", "application/json")));
        assertEquals(end, ChunkedMessageEnd$.MODULE$.apply("abc", List$.MODULE$.empty().$colon$colon(HttpHeaderFactory.create("Content-type", "application/json"))));

    }

    @Test
    public void testMessageChunk() {
        MessageChunk chunk = MessageChunkFactory.create("abc");
        assertEquals(chunk, MessageChunk.apply("abc"));

        chunk = MessageChunkFactory.create("abc", HttpParser.getCharset("UTF-8"));
        assertEquals(chunk, MessageChunk.apply("abc", HttpCharsets.UTF$minus8()));

        chunk = MessageChunkFactory.create("abc", "def");
        assertEquals(chunk, MessageChunk.apply("abc", "def"));

        chunk = MessageChunkFactory.create("abc", HttpParser.getCharset("UTF-8"), "def");
        assertEquals(chunk, MessageChunk.apply("abc", HttpParser.getCharset("UTF-8"), "def"));

        chunk = MessageChunkFactory.create("abc".getBytes());
        assertEquals(chunk, MessageChunk.apply("abc".getBytes()));
    }

    @Test
    public void testContentType() throws Exception {
        try {
            ContentTypeFactory.create("unknown");
            fail("should throw exception");
        } catch (IllegalArgumentException e) {
            // PASS
        }

        ContentType contentType = ContentTypeFactory.create("application/json; charset=UTF-8");
        assertEquals(ContentType$.MODULE$.apply(MediaTypes.application$divjson(), HttpCharsets.UTF$minus8()), contentType);

        ContentType contentType1 = ContentTypeFactory.create("application/javascript");
        assertEquals(ContentType$.MODULE$.apply(MediaTypes.application$divjavascript()), contentType1);

    }

    @Test
    public void testHttpHeaders() throws Exception {
        HttpHeader contentType = HttpHeaderFactory.create("Content-type", "application/json");
        assertTrue(contentType instanceof HttpHeaders.Content$minusType);
        assertTrue(contentType.is("content-type"));
        assertEquals("application/json", contentType.value());

        HttpHeader custom = HttpHeaderFactory.create("ABC", "def");
        assertTrue(custom instanceof HttpHeaders.RawHeader);
        assertTrue(custom.is("abc"));
        assertEquals("def", custom.value());
    }

    @Test
    public void testHttpResponse() throws Exception {
        HttpResponse defaultRes = new HttpResponseBuilder().build();
        assertEquals(StatusCodes.OK(), defaultRes.status());
        assertTrue(defaultRes.entity().isEmpty());
        assertTrue(defaultRes.headers().isEmpty());
        assertEquals(HttpProtocols.HTTP$div1$u002E1(), defaultRes.protocol());

        HttpResponse notFound = new HttpResponseBuilder().status(StatusCodes.NotFound()).build();
        assertEquals(404, notFound.status().intValue());

        HttpResponse normalResponse = new HttpResponseBuilder().entity("abc").build();
        assertEquals("abc", normalResponse.entity().asString());
        assertEquals(ContentTypeFactory.create("text/plain; charset=UTF-8"), ((HttpEntity.NonEmpty) normalResponse.entity()).contentType());

        HttpResponse streamResponse = new HttpResponseBuilder().entity("abc".getBytes()).build();
        assertEquals("abc", streamResponse.entity().asString());
        assertEquals(ContentTypeFactory.create("application/octet-stream"), ((HttpEntity.NonEmpty) streamResponse.entity()).contentType());

        ContentType contentType = ContentTypeFactory.create("application/json");
        HttpResponse withContentType = new HttpResponseBuilder().entity(contentType, "{'a':1}").build();
        assertEquals("{'a':1}", withContentType.entity().asString());
        assertEquals(contentType, ((HttpEntity.NonEmpty) withContentType.entity()).contentType());

        ArrayList<HttpHeader> httpHeaders = new ArrayList<>();
        httpHeaders.add(HttpHeaderFactory.create("x-abc", "def"));
        HttpResponse withHeaders = new HttpResponseBuilder().headers(httpHeaders).build();
        assertEquals(1, withHeaders.headers().length());
        assertEquals(httpHeaders.get(0), withHeaders.headers().apply(0));

        HttpHeader header1 = HttpHeaderFactory.create("header1", "value1");
        HttpHeader header2 = HttpHeaderFactory.create("header2", "value2");
        HttpResponse withHeaders2 = new HttpResponseBuilder().header(header1).header(header2).build();
        assertEquals(2, withHeaders2.headers().length());
        assertEquals(header1, withHeaders2.headers().apply(0));
        assertEquals(header2, withHeaders2.headers().apply(1));

        HttpResponse withProtocol = new HttpResponseBuilder().protocol("HTTP/1.0").build();
        assertEquals(HttpProtocols.HTTP$div1$u002E0(), withProtocol.protocol());
    }
}

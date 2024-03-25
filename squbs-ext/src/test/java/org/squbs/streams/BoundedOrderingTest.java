/*
 * Copyright 2018 PayPal
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
package org.squbs.streams;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.ActorMaterializer;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class BoundedOrderingTest {

    static final ActorSystem system = ActorSystem.create("BoundedOrderingTest");
    static final Materializer mat = ActorMaterializer.create(system);

    @AfterClass
    public static void cleanUp() {
        system.terminate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidWithin() {
        BoundedOrdering.create(-1, 1, i -> i + 1, Function.identity());
    }

    @Test
    public void testRetainOrder() throws Exception {
        Flow<Integer, Integer, NotUsed> boundedOrdering = BoundedOrdering.create(5, 1, i -> i + 1, i -> i);
        List<Integer> input = Arrays.asList(1, 2, 3, 4, 5);
        CompletionStage<List<Integer>> outputF =
                Source.from(input).via(boundedOrdering).toMat(Sink.seq(), Keep.right()).run(mat);
        List<Integer> output = outputF.toCompletableFuture().get();
        Assert.assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++) {
            Assert.assertEquals(input.get(i), output.get(i));
        }
    }

    @Test
    public void testReboundedOrdering() throws Exception {
        Flow<Integer, Integer, NotUsed> boundedOrdering = BoundedOrdering.create(5, 1, i -> i + 1, i -> i);
        List<Integer> input = Arrays.asList(2, 3, 4, 1, 5, 7, 8, 6, 9, 10);
        CompletionStage<List<Integer>> outputF =
                Source.from(input).via(boundedOrdering).toMat(Sink.seq(), Keep.right()).run(mat);
        List<Integer> output = outputF.toCompletableFuture().get();
        Collections.sort(input);
        Assert.assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++) {
            Assert.assertEquals(input.get(i), output.get(i));
        }
    }

    @Test
    public void testReorderOutside() throws Exception {
        Flow<Integer, Integer, NotUsed> boundedOrdering = BoundedOrdering.create(5, 1, i -> i + 1, i -> i);
        List<Integer> input = Arrays.asList(1, 3, 4, 5, 6, 7, 8, 9, 2, 10);
        CompletionStage<List<Integer>> outputF =
                Source.from(input).via(boundedOrdering).toMat(Sink.seq(), Keep.right()).run(mat);
        List<Integer> output = outputF.toCompletableFuture().get();
        Assert.assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++) {
            Assert.assertEquals(input.get(i), output.get(i));
        }
    }

    @Test
    public void testIgnoreMissing() throws Exception {
        Flow<Integer, Integer, NotUsed> boundedOrdering = BoundedOrdering.create(5, 1, i -> i + 1, i -> i);
        List<Integer> input = Arrays.asList(1, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        CompletionStage<List<Integer>> outputF =
                Source.from(input).via(boundedOrdering).toMat(Sink.seq(), Keep.right()).run(mat);
        List<Integer> output = outputF.toCompletableFuture().get();
        Assert.assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++) {
            Assert.assertEquals(input.get(i), output.get(i));
        }
    }

    @Test
    public void testMessageType() throws Exception {
        Flow<Element, Element, NotUsed> boundedOrdering = BoundedOrdering.create(5, 1L, i -> i + 1L, e -> e.id);
        List<Element> input = Arrays.asList(new Element(1L, "one"), new Element(3L, "three"),
                new Element(5L, "five"), new Element(2L, "two"), new Element(6L, "six"),
                new Element(7L, "seven"), new Element(8L, "eight"), new Element(9L, "nine"),
                new Element(10L, "ten"), new Element(4L, "four"));

        CompletionStage<List<Element>> outputF =
                Source.from(input).via(boundedOrdering).toMat(Sink.seq(), Keep.right()).run(mat);

        List<Element> output = outputF.toCompletableFuture().get();

        List<Element> wisb = Arrays.asList(new Element(1L, "one"), new Element(2L, "two"),
                new Element(3L, "three"), new Element(5L, "five"), new Element(6L, "six"),
                new Element(7L, "seven"), new Element(8L, "eight"), new Element(9L, "nine"),
                new Element(10L, "ten"), new Element(4L, "four"));

        Assert.assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++) {
            Assert.assertEquals(wisb.get(i), output.get(i));
        }
    }

    @Test
    public void testCustomComparator() throws Exception {
        Flow<Element2, Element2, NotUsed> boundedOrdering = BoundedOrdering.create(5, "2",
                s -> String.valueOf(Integer.parseInt(s) + 2), e -> e.id, Comparator.comparingInt(Integer::parseInt));

        List<Element2> input = Arrays.asList(new Element2("2", "one"),
                new Element2("6", "three"), new Element2("10", "five"),
                new Element2("4", "two"), new Element2("12", "six"),
                new Element2("14", "seven"), new Element2("16", "eight"),
                new Element2("18", "nine"), new Element2("20", "ten"),
                new Element2("8", "four"));

        List<Element2> wisb = Arrays.asList(new Element2("2", "one"),
                new Element2("4", "two"), new Element2("6", "three"),
                new Element2("10", "five"), new Element2("12", "six"),
                new Element2("14", "seven"), new Element2("16", "eight"),
                new Element2("18", "nine"), new Element2("20", "ten"),
                new Element2("8", "four"));

        CompletionStage<List<Element2>> outputF =
                Source.from(input).via(boundedOrdering).toMat(Sink.seq(), Keep.right()).run(mat);
        List<Element2> output = outputF.toCompletableFuture().get();

        Assert.assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++) {
            Assert.assertEquals(wisb.get(i), output.get(i));
        }
    }


    static class Element {
        final Long id;
        final String content;

        Element(Long id, String content) {
            this.id = id;
            this.content = content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Element element = (Element) o;
            return Objects.equals(id, element.id) &&
                    Objects.equals(content, element.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, content);
        }

        @Override
        public String toString() {
            return "Element{" +
                    "id=" + id +
                    ", content='" + content + '\'' +
                    '}';
        }
    }

    static class Element2 {
        final String id;
        final String content;

        Element2(String id, String content) {
            this.id = id;
            this.content = content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Element2 element2 = (Element2) o;
            return Objects.equals(id, element2.id) &&
                    Objects.equals(content, element2.content);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, content);
        }

        @Override
        public String toString() {
            return "Element2{" +
                    "id='" + id + '\'' +
                    ", content='" + content + '\'' +
                    '}';
        }
    }
}

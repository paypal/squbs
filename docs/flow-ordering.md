# Bounded Ordering

### Overview

Certain stream topologies such as ones calling HTTP client or `mapAsyncUnordered` allow stream elements to go out of order. The bounded ordering stage allows streams to get back in order when needed. This ordering is best effort using a sliding window algorithm. If a stream is out of order within the given bounded sliding window size, total ordering can be achieved as a result. However, if elements are out of order beyond the sliding window size, the stream cannot and will not wait. The out-of-order elements will either be skipped, or will be passed down out-of-order if it arrives late beyond the window. This bounded sliding window limit is to prevent stream starvation and memory leaks from buffers building up.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The bounded ordering functionality is provided by the `BoundedOrdering` component exposed as a `Flow` component that can be connected to a stream `Source` or another `Flow` component using the `via` operator. The elements passing through `BoundedOrdering` are expected to have a predictable, orderable, and continuous id we can derive from the element. This id is commonly a `Long` but could be any other type provided it's order is predictable.

The `BoundedOrdering` creation takes a few inputs:

1. The `maxBounded` parameter defines the sliding window size for which `BoundedOrdering` will wait for the out-of-order element.
2. The `initialId` parameter is the expected initial idid for the first element. This way the stream would know if the first element went missing or out-of-order.
3. The `nextId` parameter specifies the function to derive the subsequent element's id from the current element's id.
4. The `getId` parameter specifies the function used to extract the id from the element.
5. Optionally, a `Comparator` may be passed if the system does not know how to compare and order the id type.

Initialization and usage can be seen in the following examples:

##### Scala

```scala
// This is the sample message type.
case class Element(id: Long, content: String)

// Create the BoundedOrdering component.
val boundedOrdering = BoundedOrdering[Element, Long](maxBounded = 5, 1L, _ + 1L, _.id)

// Then just use it in the stream.
Source(input).via(boundedOrdering).to(Sink.ignore).run()
```

##### Java

```java
// This is the sample message type.
static class Element {
    final Long id;
    final String content;

    // Omitting constructor, hashCode, equals, and toString for brevity.
    // Do not forget to implement.
}

// Create the BoundeOrdering component.
Flow<Element, Element, NotUsed> boundedOrdering = BoundedOrdering.create(5, 1L, i -> i + 1L, e -> e.id);

// Then just use it in the stream.
Source.from(input).via(boundedOrdering).to(Sink.ignore()).run(mat);
```

#### Note

This stage may detach downstream and upstream demand to some extent, temporarily. This will happen when elements arrive out of order. Out-of-order elements will be buffered till its order has arrived, or the bounds have been reached. When this happens demand is generated upstream from this component without emitting elements downstream until given conditions have been reached. 
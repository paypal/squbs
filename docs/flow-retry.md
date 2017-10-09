# Retry stage

### Overview

Some stream use cases may require retrying of requests after a failure response.  squbs provides a `RetryBidi` Akka Streams stage to add a retry capability to
 streams that need to add retries for any failing requests.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The retry stage functionality is provided via a `BidiFlow` that can be connected to flows via the `join` operator.  The retry `BidiFlow` will perform some
 specified maximum number of retries of any failures from downstream. A failure is determined by either a passed in failure decider function or a Failure,
 if a failure decider function is not supplied.

If all the retried attempts fail then the last failure for that request is emitted.  The retry stage requires a `Context` to be carried around for each
 element.  This is needed to uniquely identify each element (see [Context to Unique Id Mapping](#context-to-unique-id-mapping) section for more details).

##### Scala

```scala
val retryBidi = RetryBidi[String, Long](maxRetries = 10)
val flow = Flow[Try[String], Long].map(s => findAnEnglishWordThatStartWith(s))

Source("a" :: "b" :: "c" :: Nil)
  .via(retryBidi.join(flow))
  .runWith(Sink.seq)
```

##### Java

```java
final BidiFlow<Pair[String, Context], Pair[String, Context],
    Pair[Try[String], Context], Pair[Try[String], Context], NotUsed> retryBidi =
    RetryBidi.create(2);

final Flow<String, String, NotUsed> flow =
    Flow.<String>create().map(s -> findAnEnglishWordThatStartWith(s));

Source.from(Arrays.asList("a", "b", "c"))
    .via(retryBidi.join(flow))
    .runWith(Sink.seq(), mat);
```

#### Context to Unique Id Mapping

The `Context` type itself might be used as a unique id.  However, in many scenarios, `Context` contains more than the unique id itself or the unique id might
 be retrieved as a mapping from the `Context`.  squbs allows different options to provide a unique id:

   * `Context` itself is a type that can be used as a unique id, e.g., `Int`, `Long`, `java.util.UUID`
   * `Context` extends `UniqueId.Provider` and implements `def uniqueId`
   * `Context` is wrapped with `UniqueId.Envelope`
   * `Context` is mapped to a unique id by calling a function

With the first three options, a unique id can be retrieved directly through the context.

For the last option, `RetryBidi` allows a function to be passed in as a parameter.

###### Scala

The following API can be used to pass a uniqueId mapper:

```scala
RetryBidi[In, Out, Context](maxRetries: Int, uniqueIdMapper: Context => Option[Any])
```

This `BidiFlow` can be joined with any flow that takes in a `(In, Context)` and outputs a `(Out, Context)`.

```scala
case class MyContext(s: String, uuid: UUID)

val retryBidi = RetryBidi[String, String, MyContext](maxRetries = 2, (context: MyContext) => Some(context.uuid))
val flow = Flow[(String, MyContext)].mapAsyncUnordered(10) { elem =>
  (ref ? elem).mapTo[(String, MyContext)]
}

Source("a" :: "b" :: "c" :: Nil)
  .map( _ -> MyContext("dummy", UUID.randomUUID))
  .via(retryBidi.join(flow))
  .runWith(Sink.seq)
```

###### Java

The following API is used to pass a uniqueId mapper:

```java
public class RetryBidi {
    public static <In, Out, Context> BidiFlow<Pair<In, Context>,
                                              Pair<In, Context>,
                                              Pair<Try<Out>, Context>,
                                              Pair<Try<Out>, Context>,
                                              NotUsed>
    create(Long maxRetries, Function<Context, Optional<Object>> uniqueIdMapper);
}
```

This `BidiFlow` can be joined with any flow that takes in a `akka.japi.Pair<In, Context>` and outputs a `akka.japi.Pair<Try<Out>, Context>`.

```java
class MyContext {
    private String s;
    private UUID uuid;

    public MyContext(String s, UUID uuid) {
        this.s = s;
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }
};

final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>,
               Pair<Try<String>, MyContext>, Pair<Try<String>, MyContext>, NotUsed>
                retryBidi = RetryBidi.create(maxRetries, context -> Optional.of(context.uuid));

final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
        Flow.<Pair<String, MyContext>>create()
                .mapAsyncUnordered(10, elem -> ask(ref, elem, 5000))
                .map(elem -> (Pair<String, MyContext>)elem);

Source.from(Arrays.asList("a", "b", "c"))
        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
        .via(retryBidi.join(flow))
        .runWith(Sink.seq(), mat);

```

#### Failure decider

By default, any `Failure` from the joined `Flow` is considered a failure for retry purposes.  However, the `Retry` stage also accepts an optional
 `failureDecider` parameter to more finely control what elements from the joined `Flow` should actually be treated as failures that should be retried.

##### Scala

A function of type `Try[Out] => Boolean` can be provided via the `failureDecider` parameter. Below is an example where, along with any `Failure` message,
Any response with failing http status code is also considered a failure:

```scala

val failureDecider = (tryResponse: Try[HttpResponse]) => tryResponse.isFailure || tryResponse.get().status().isFailure()

val retryBidi = RetryBidi[Request, Response, MyContext](maxRetries = 3, (context: MyContext) => Some(context.uuid), failureDecider = Option(failureDecider), OverflowStrategy.backpressure())

```

##### Java

A `Function<Try<Out>, Boolean>` can be provided via Retry Optional `failureDecider` parameter to `create` method.  Below is an example where,
along with any `Failure` message, a `Success` of `HttpResponse` with status code `400` and above is also considered a failure:

```java

final Function<Try<HttpResponse>, Boolean> failureDecider =
tryResponse -> tryResponse.isFailure() || tryResponse.get().status().isFailure());

final BidiFlow<Pair<HttpResponse, MyContext>, Pair<HttpResponse, MyContext>, Pair<Try<HttpResponse>, MyContext>,
    Pair<Try<HttpResponse>, MyContext>, NotUsed> retryFlow =
    RetryBidi.create(3L, Optional.of(failureDecider), OverflowStrategy.backpressure());

```

#### Retries with a delay

By default, any failures from the joined `Flow` are immediately retried if we can push to joined Flow.  However, the `Retry` stage also accepts an optional
 `Duration` delay parameter to introduce a timed delay between each subsequent retry attempt.

For example to create a Retry stage that delays 200 milliseconds during retries:
##### Scala

```scala
val retryBidi = RetryBidi[String, Long](maxRetries = 3, delay = 200 millis)
```

##### Java

```java
final BidiFlow<Pair[String, Context], Pair[String, Context],
    Pair[Try[String], Context], Pair[Try[String], Context], NotUsed> retryBidi =
    RetryBidi.create(3, Duration.create("200 millis"));

```

A optional binary exponential backoff factor can also be specified to increase the delay duration on each subsequent
retry attempt (upto a maximum factor of 100 retry duration)

For example to add a backoff factor of 1 for above example:

##### Scala

```scala
val retryBidi = RetryBidi[String, Long](maxRetries = 3, delay = 200 millis, expBackoff = 1.0)
```

##### Java

```java
final BidiFlow<Pair[String, Context], Pair[String, Context],
    Pair[Try[String], Context], Pair[Try[String], Context], NotUsed> retryBidi =
    RetryBidi.create(2, Duration.create("200 millis"), 1.0);

```
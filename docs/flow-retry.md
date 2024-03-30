# Retry stage

### Overview

Some stream use cases may require retrying of requests after a failure response.  squbs provides a `Retry` pekko Streams
stage to add a retry capability to streams that need to add retries for any failing requests.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The retry stage functionality is provided via a `BidiFlow` that can be connected to flows via the `join` operator.  The
retry `BidiFlow` will perform some specified maximum number of retries of any failures from downstream. A failure is
determined by either a passed in failure decider function or a Failure, if a failure decider function is not supplied.

If all the retried attempts fail then the last failure for that request is emitted.  The retry stage requires a
`Context` to be carried around for each element.  This is needed to _uniquely
identify_ each element.  Please see [Context to Unique Id
Mapping](#context-to-unique-id-mapping) section for more details.

##### Scala

```scala
val retry = Retry[String, String, Long](max = 10)
val flow = Flow[(String, Long)].map { case (s, ctx) => (findAnEnglishWordThatStartWith(s), ctx) }

Source("a" :: "b" :: "c" :: Nil)
  .zipWithIndex
  .via(retry.join(flow))
  .runWith(Sink.foreach(println))
```

##### Java

```java
final BidiFlow<Pair<String, Long>,
                Pair<String, Long>,
                Pair<Try<String>, Long>,
                Pair<Try<String>, Long>, NotUsed> retry = Retry.create(2);

final Flow<Pair<String, Long>, Pair<Try<String>, Long>, NotUsed> flow =
        Flow.<Pair<String, Long>>create()
                .map(p -> Pair.create(findAnEnglishWordThatStartsWith.apply(p.first()), p.second()));

Source.from(Arrays.asList("a", "b", "c"))
        .zipWithIndex()
        .via(retry.join(flow))
        .runWith(Sink.foreach(System.out::println), mat);
```

#### Context to Unique Id Mapping

The `Context` type itself might be used as a unique id.  However, in many scenarios, `Context` contains more than the
unique id itself or the unique id might be retrieved as a mapping from the `Context`.  squbs allows different options to
provide a unique id:

   * `Context` itself is a type that can be used as a unique id, e.g., `Int`, `Long`, `java.util.UUID`
   * `Context` extends `UniqueId.Provider` and implements `def uniqueId`
   * `Context` is wrapped with `UniqueId.Envelope`
   * `Context` is mapped to a unique id by calling a function

With the first three options, a unique id can be retrieved directly through the context.

For the last option, `Retry` allows a function to be passed in through `RetrySettings`.


###### Scala

```scala
case class MyContext(s: String, id: Long)

val settings =
  RetrySettings[String, String, MyContext](maxRetries = 3)
    .withUniqueIdMapper(context => context.id)
    
val retry = Retry(settings)    
```

###### Java

```java
class MyContext {
    private String s;
    private long id;

    public MyContext(String s, long id) {
        this.s = s;
        this.id = id;
    }

    public long id() {
        return id;
    }
}

final RetrySettings settings =
        RetrySettings.<String, String, MyContext>create(3)
                .withUniqueIdMapper(ctx -> ctx.id());
                
final BidiFlow<Pair<String, MyContext>,
               Pair<String, MyContext>,
               Pair<Try<String>, MyContext>,
               Pair<Try<String>, MyContext>, NotUsed> retry = Retry.create(settings);
```

##### Important note

As mentioned earlier, a `RetryStage` uses the `Context` to identify elements that should be retried.  If the downstream
modifies the `Context` such that it can no longer be used to uniquely identify elements, the tracked elements will never
be removed, essentially becoming a memory leak.  Furthermore, those elements will never be retried, and it is likely the
flow will unable to materialize a value.  Consequently, it is **very** important to understand the concepts above and to
make sure the downstream does not adversely affect them.

#### Failure decider

By default, any `Failure` from the joined `Flow` is considered a failure for retry purposes.  However, the `Retry` stage
also accepts an optional `failureDecider` parameter, via `RetrySettings`, to more finely control what elements from the
joined `Flow` should actually be treated as failures that should be retried.

##### Scala

A function of type `Try[Out] => Boolean` can be provided to `RetrySettings` via the `withFailureDecider` call. Below is
an example where, along with any `Failure` message, a response with failing http status code is also considered a
failure:

```scala
val failureDecider = (tryResponse: Try[HttpResponse]) => tryResponse.isFailure || tryResponse.get.status.isFailure

val settings =
  RetrySettings[HttpRequest, HttpResponse, MyContext](max = 3)
    .withFailureDecider(failureDecider)

val retry = Retry(settings)

```

##### Java

A `Function<Try<Out>, Boolean>` can be provided to `RetrySettings` via the `withFailureDecider` call.  Below is an
example where, along with any `Failure` message, a `Success` of `HttpResponse` with status code `400` and above is also
considered a failure:

```java
final Function<Try<HttpResponse>, Boolean> failureDecider =
        tryResponse -> tryResponse.isFailure() || tryResponse.get().status().isFailure();

final RetrySettings settings =
        RetrySettings.<HttpRequest, HttpResponse, MyContext>create(1)
                .withFailureDecider(failureDecider);

final BidiFlow<Pair<HttpRequest, MyContext>,
               Pair<HttpRequest, MyContext>,
               Pair<Try<HttpResponse>, MyContext>,
               Pair<Try<HttpResponse>, MyContext>, NotUsed> retry = Retry.create(settings);

```

#### Retries with a delay duration

By default, any failures pulled from the joined `Flow` are immediately attempted to be retried.  However, the `Retry`
stage also accepts an optional delay `Duration` parameter to add a timed delay between each subsequent retry attempt.
This duration is the minimum delay duration from when a failure is pulled from the joined flow to when it is
re-attempted (pushed) again to the joined flow.  For example, to create a `Retry` stage that delays 200 milliseconds
during retries:

##### Scala

```scala
val settings = RetrySettings[String, String, Context](max = 3).withDelay(1 second)

val retry = Retry(settings)
```

##### Java

```java
final RetrySettings settings =
        RetrySettings.<String, String, Context>create(3)
                .withDelay(Duration.create("200 millis"));
        
final BidiFlow<Pair<String, Context>,
               Pair<String, Context>,
               Pair<Try<String>, Context>,
               Pair<Try<String>, Context>, NotUsed> retry = Retry.create(settings);

```

##### Exponential backoff
An optional exponential backoff factor can also be specified to increase the delay duration on each subsequent retry
attempt (up to a maximum delay duration).  In the following examples, the first failure of any element will be retried
after a delay of 200ms, and then any second attempt will be retried after 800ms.  In general the retry delay duration
will continue to increase using the formula `delay * N ^ exponentialBackOff` (where N is the retry number).

###### Scala

```scala
val settings =
  RetrySettings[String, String, Context](max = 3)
    .withDelay(200 millis)
    .withExponentialBackoff(2)

val retry = Retry(settings)
```

###### Java

```java
final RetrySettings settings =
        RetrySettings.create<String, String, Context>.create(3)
                .withDelay(Duration.create("200 millis"))
                .withExponentialBackoff(2.0);
    

final BidiFlow<Pair<String, Context>,
               Pair<String, Context>,
               Pair<Try<String>, Context>,
               Pair<Try<String>, Context>, NotUsed> retry = Retry.create(settings);

```

##### Maximum delay
An optional maximum delay duration can also be specified to provide an upper bound on the exponential backoff delay
duration.  If no maximum delay is specified the exponential backoff will continue to increase the retry delay duration
until the number of maxRetries.

###### Scala

```scala
val settings =
  RetrySettings[String, String, Context](max = 3)
    .withDelay(200 millis)
    .withExponentialBackoff(2)
    .withMaxDelay(400 millis)

val retry = Retry(settings)
```

###### Java

```java
final RetrySettings settings =
        RetrySettings.create<String, String, Context>.create(3)
                .withDelay(Duration.create("200 millis"))
                .withExponentialBackoff(2.0)
                withMaxDelay(Duration.create("400 millis"));

final BidiFlow<Pair<String, Context>,
               Pair<String, Context>,
               Pair<Try<String>, Context>,
               Pair<Try<String>, Context>, NotUsed> retry = Retry.create(settings);
```

##### Configuring the threshold for backpressure

If the joined flow keeps returning failures, `Retry` starts back pressuring when the elements waiting to be retried
reaches to a certain threshold.  By default, the threshold is equal to the internal buffer size of `Retry` pekko Stream
`GraphStage` (please see [pekko Stream
Attributes](https://doc.pekko.io/docs/pekko/current/stream/stream-composition.html#attributes)).  The threshold can be
made independent of internal buffer size by calling `withMaxWaitingRetries`:


##### Scala

```scala
val settings = RetrySettings[String, String, Context](max = 3).withMaxWaitingRetries(50)

val retry = Retry(settings)
```

##### Java

```java
final RetrySettings settings =
        RetrySettings.<String, String, Context>create(3)
                .withMaxWaitingRetries(50)
        
final BidiFlow<Pair<String, Context>,
               Pair<String, Context>,
               Pair<Try<String>, Context>,
               Pair<Try<String>, Context>, NotUsed> retry = Retry.create(settings);
```

#### Metrics

`Retry` supports Codahale counters for state and failure/success rate of elements passing through it.  Metrics can be
enabled by calling `withMetrics` with a name and an actor system.

The following counters are provided:

  * Count of all retry elements ("<name>.retry-count")
  * Count of all failed elements ("<name>.failed-count")
  * Count of all successful elements ("<name>.success-count")
  * Current size of the retry registry
  * Current size of the retry queue

##### Scala

```scala
val settings = RetrySettings[String, String, Context](max = 3)
  .withMetrics("myRetry") // Takes implicit ActorSystem

val retry = Retry(settings)
```

##### Java

```java
final RetrySettings settings =
        RetrySettings.<String, String, Context>create(3)
                .withMetrics("myRetry", system)

final BidiFlow<Pair<String, Context>,
               Pair<String, Context>,
               Pair<Try<String>, Context>,
               Pair<Try<String>, Context>, NotUsed> retry = Retry.create(settings);
```

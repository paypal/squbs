# Circuit Breaker

### Overview

Pekko Streams and Pekko HTTP are great technologies to build highly resilient systems. They provide back-pressure to ensure you do not overload the system and the slowness of one component does not cause its work queue to pile up and end up in memory leaks. But, we need another safeguard to ensure our service stays responsive in the event of external or internal failures, and have alternate paths to satisfying the requests/messages due to such failures. If a downstream service is not responding or slow, we could alternatively try another service or fetch cached results instead of back-pressuring the stream and potentially the whole system.

squbs introduces `CircuitBreaker` Pekko Streams `GraphStage` to provide circuit breaker functionality for streams.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The circuit breaker functionality is provided as a `BidiFlow` that can be connected to a flow via the `join` operator.  `CircuitBreaker` might potentially change the order of messages, so it requires a `Context` to be carried around.  In addition, it needs to be able to uniquely identify each element for its internal mechanics.  The requirement is that either the `Context` itself or a mapping from `Context` should be able to uniquely identify an element (see [Context to Unique Id Mapping](#context-to-unique-id-mapping) section for more details).  Along with the `Context`, a `Try` is pushed downstream: 

Circuit is `Closed`:

   * If an output `msg` is provided by the wrapped flow within the timeout, then `(Success(msg), context)` is passed down.
   * Otherwise, a `(Failure(FlowTimeoutException()), context)` is pushed to downstream.


Circuit is `Open`:

   * The result of `fallback` function, along with the `context`, is pushed to downstream if one provided
   * Otherwise, a `(Failure(CircuitBreakerOpenException()), context)` is pushed to downstream 	


Circuit is `HalfOpen`:
   
   * The first request/element is let to go through the wrapped flow, and the behavior should be same as `Closed` state.
   * Rest of the elements are short circuited and the behaviour is same to `Open` state. 	

The state of the circuit breaker is hold in a `CircuitBreakerState` implementation.  The default implementation `AtomicCircuitBreakerState ` is based on `Atomic` variables, which allows it to be updated concurrently across multiple flow materializations.   

##### Scala

```scala
import org.squbs.streams.circuitbreaker.CircuitBreakerSettings

val state = AtomicCircuitBreakerState("sample", 2, 100 milliseconds, 1 second)
val settings = CircuitBreakerSettings[String, String, UUID](state)
val circuitBreaker = CircuitBreaker(settings)

val flow = Flow[(String, UUID)].mapAsyncUnordered(10) { elem =>
  (ref ? elem).mapTo[(String, UUID)]
}

Source("a" :: "b" :: "c" :: Nil)
  .map(s => (s, UUID.randomUUID()))
  .via(circuitBreaker.join(flow))
  .runWith(Sink.seq)
```

##### Java

For Java, use `CircuitBreakerSettings` from the `org.squbs.streams.circuitbreaker.japi` package:

```java
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;

final CircuitBreakerState state =
        AtomicCircuitBreakerState.create(
                "sample",
                2,
                FiniteDuration.apply(100, TimeUnit.MILLISECONDS),
                FiniteDuration.apply(1, TimeUnit.SECONDS),
                system.dispatcher(),
                system.scheduler());

final CircuitBreakerSettings<String, String, UUID> settings = CircuitBreakerSettings.create(state);

final BidiFlow<Pair<String, UUID>,
               Pair<String, UUID>,
               Pair<String, UUID>,
               Pair<Try<String>, UUID>, NotUsed> circuitBreaker = CircuitBreaker.create(settings);

final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
        Flow.<Pair<String, UUID>>create()
                .mapAsyncUnordered(10, elem -> ask(ref, elem, 5000))
                .map(elem -> (Pair<String, UUID>)elem);


Source.from(Arrays.asList("a", "b", "c"))
        .map(s -> Pair.create(s, UUID.randomUUID()))
        .via(circuitBreaker.join(flow))
        .runWith(Sink.seq(), mat);
```

#### Fallback Response

`CircuitBreakerSettings` optionally takes a fallback function that gets called when the circuit is `Open`.

##### Scala

A function of type `In => Try[Out]` can be provided via `withFallback` function:

```scala
import org.squbs.streams.circuitbreaker.CircuitBreakerSettings

val settings =
  CircuitBreakerSettings[String, String, UUID](state)
    .withFallback((elem: String) => Success("Fallback Response!"))
```

##### Java

A `Function<In, Try<Out>>` can be provided via `withFallback` function:

```java
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;

CircuitBreakerSettings settings =
        CircuitBreakerSettings.<String, String, UUID>create(state)
                .withFallback(s -> Success.apply("Fallback Response!"));
```

#### Failure Decider

By default, any `Failure` from the joined `Flow` is considered a problem and causes the circuit breaker failure count to be incremented.  However, `CircuitBreakerSettings` also accepts an optional `failureDecider` to decide on whether an element passed by the joined `Flow` is actually considered a failure.  For instance, if Circuit Breaker is joined with an Pekko HTTP flow, a `Success` Http Response with status code 500 internal server error should be considered a failure.

##### Scala

A function of type `Try[Out] => Boolean` can be provided via `withFailureDecider` function. Below is an example where, along with any `Failure` message, a `Success` of `HttpResponse` with status code `400` and above is also considered a failure:

```scala
import org.squbs.streams.circuitbreaker.CircuitBreakerSettings

val settings =
  CircuitBreakerSettings[HttpRequest, HttpResponse, UUID](state)
    .withFailureDecider(tryHttpResponse => tryHttpResponse.isFailure || tryHttpResponse.get.status.isFailure)
```

##### Java

A `Function<Try<Out>, Boolean>` can be provided via `withFailureDecider` function.  Below is an example where, along with any `Failure` message, a `Success` of `HttpResponse` with status code `400` and above is also considered a failure:

```java
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;

CircuitBreakerSettings settings =
        CircuitBreakerSettings.<HttpRequest, HttpResponse, UUID>create(state)
                .withFailureDecider(
                        tryHttpResponse -> tryHttpResponse.isFailure() || tryHttpResponse.get().status().isFailure());
```

#### Creating CircuitBreakerState from a configuration

While `AtomicCircuitBreakerState` has programmatic API for configuration, it also allows the configuration to be provided through a `Config` object.  The `Config` can partially define some settings, and for the rest, it will fallback to default values.  Please see [here](../squbs-ext/src/main/resources/reference.conf) for the default circuit breaker configuration.

##### Scala

```scala
val config = ConfigFactory.parseString(
  """
    |max-failures = 5
    |call-timeout = 50 ms
    |reset-timeout = 100 ms
    |max-reset-timeout = 2 seconds
    |exponential-backoff-factor = 2.0
  """.stripMargin)

val state = AtomicCircuitBreakerState("sample", config)
```

##### Java

```java
Config config = ConfigFactory.parseString(
        "max-failures = 5\n" +
        "call-timeout = 50 ms\n" +
        "reset-timeout = 100 ms\n" +
        "max-reset-timeout = 2 seconds\n" +
        "exponential-backoff-factor = 2.0");
        
final CircuitBreakerState state = AtomicCircuitBreakerState.create("sample", config, system);
```

#### Circuit Breaker across materializations

Please note, in many scenarios, the same circuit breaker instance is used across multiple materializations of the same flow.  For such scenarios, make sure to use a `CircuitBreakerState` instance that can be modified concurrently.  The default implementation `AtomicCircuitBreakerState` uses `Atomic` variables and can be used across multiple materializations.  More implementations can be introduced in the future.

#### Context to Unique Id Mapping

`Context` itself might be used as a unique id.  However, in many scenarios, `Context` contains more than the unique id itself or the unique id might be retrieved as a mapping from the `Context`.  squbs allows different options to provide a unique id:

   * `Context` itself is a type that can be used as a unique id, e.g., `Int`, `Long`, `java.util.UUID`
   * `Context` extends `UniqueId.Provider` and implements `def uniqueId`
   * `Context` is wrapped with `UniqueId.Envelope`
   * `Context` is mapped to a unique id by calling a function


With the first three options, a unique id can be retrieved directly through the context.  For the last option, `CircuitBreakerSettings` allows a function to be provided.

##### Scala

A function of type `Context => Any` can be provided via `withUniqueIdMapper` function:

```scala
import org.squbs.streams.circuitbreaker.CircuitBreakerSettings

case class MyContext(s: String, id: Long)

val settings =
  CircuitBreakerSettings[String, String, MyContext](state)
    .withUniqueIdMapper(context => context.id)
```

##### Java

A `Function<Context, Any>` can be provided via `withUniqueIdMapper` function:

```java
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;

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

CircuitBreakerSettings settings =
        CircuitBreakerSettings.<String, String, MyContext>create(state)
                .withUniqueIdMapper(context -> context.id());
```

#### Notifications

An `ActorRef` can be subscribed to receive all `TransitionEvents` or any transition event that it is interested in, e.g., `Closed`, `Open`, `HalfOpen`.

##### Scala

Here is an example that registers an `ActorRef` to receive events when circuit transitions to `Open` state:

```scala
state.subscribe(self, Open)
```

##### Java

Here is an example that registers an `ActorRef` to receive events when circuit transitions to `Open` state:

```java
state.subscribe(getRef(), Open.instance());
```

#### Metrics

The `CircuitBreakerState` keeps Codahale meters for:

* Success count
* Failure count
* Short circuit times

It also keeps a `Gauge` for the circuit breaker state.

To differentiate metrics across instance, `CircuitBreakerState` implementation require a name to passed in.

`CircuitBreakerState` also allows a `MetricRegistry` instance to be passed in.  If no `MetricRegistry` is passed in, it creates one internally. 

#### Potential effects of Circuit Open state on throughput

If the upstream does not control the throughput, then the throughput of the stream might temporarily increase once the circuit is `Open`:  The downstream demand will be addressed with short circuit/fallback messages, which might (or might not) take less time than it takes the joined `Flow` to process an element.  To eliminate this problem, a throttle can be applied specifically for circuit breaker `Open` messages (or fallback messages).

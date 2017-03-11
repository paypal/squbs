# Circuit Breaker

### Overview

Akka Streams and Akka HTTP are great technologies to build highly resilient systems. They provide back-pressure to ensure you do not overload the system and the slowness of one component does not cause its work queue to pile up and end up in memory leaks. But, we need another safeguard to ensure our service stays responsive in the event of external or internal failures, and have alternate paths to satisfying the requests/messages due to such failures. If a downstream service is not responding or slow, we could alternatively try another service or fetch cached results instead of back-pressuring the stream and potentially the whole system.

squbs introduces `CircuitBreakerBidi` Akka Streams `GraphStage` to provide circuit breaker functionality for streams.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The circuit breaker functionality is provided as a `BidiFlow` that can be connected to a flow via the `join` operator.  `CircuitBreakerBidi` might potentially change the order of messages, so it requires a `Context` to be carried around.  The requirement is that either the `Context` itself or a mapping from `Context` should be able to uniquely identify an element (see [Context to Unique Id Mapping](#context-to-unique-id-mapping) section for more details).  Along with the `Context`, a `Try` is pushed downstream: 

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

#### Scala

```scala
val circuitBreakerState = AtomicCircuitBreakerState("sample", system.scheduler, 2, timeout, 10 milliseconds)
val circuitBreakerBidiFlow = CircuitBreakerBidiFlow[String, String, UUID](circuitBreakerState)

val flow = Flow[(String, UUID)].mapAsyncUnordered(10) { elem =>
  (ref ? elem).mapTo[(String, UUID)]
}

Source("a" :: "b" :: "c" :: Nil)
  .map(s => (s, UUID.randomUUID()))
  .via(circuitBreakerBidiFlow.join(flow))
  .runWith(Sink.seq)
```

#### Java

```java
final CircuitBreakerState circuitBreakerState =
        AtomicCircuitBreakerState.create(
                "sample",
                system.scheduler(),
                2,
                timeout,
                FiniteDuration.apply(10, TimeUnit.MILLISECONDS),
                system.dispatcher());

final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed>
        circuitBreakerBidiFlow = CircuitBreakerBidiFlow.create(circuitBreakerState);

final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
        Flow.<Pair<String, UUID>>create()
                .mapAsyncUnordered(10, elem -> ask(ref, elem, 5000))
                .map(elem -> (Pair<String, UUID>)elem);


Source.from(Arrays.asList("a", "b", "c"))
        .map(s -> Pair.create(s, UUID.randomUUID()))
        .via(circuitBreakerBidiFlow.join(flow))
        .runWith(Sink.seq(), mat);
```

#### Fallback Response

`CircuitBreakerBidiFlow` optionally takes a fallback function that gets called when the circuit is `Open` to provide an alternative path.

##### Scala
Scala API takes an `Option` of function `Option[((In, Context)) => (Try[Out], Context)]`:

```scala
CircuitBreakerBidiFlow[String, String, UUID](
      circuitBreakerState,
      fallback = Some((elem: (String, UUID)) => (Success("fb"), elem._2)),
      failureDecider = None)
```

##### Java

Java API takes an `Optional` of `Optional<Function<Pair<In, Context>, Pair<Try<Out>, Context>>>`:

```java
CircuitBreakerBidiFlow.create(
        circuitBreakerState,
        Optional.of(pair -> Pair.create(Success.apply("fallbackResponse"), pair.second())),
        Optional.empty());
```

#### Failure Decider

By default, any `Failure` from the joined `Flow` is considered a problem and causes the circuit breaker failure count to be incremented.  However, `CircuitBreakerBidi` also accepts an optional `failureDecider` to decide on if an element passed by the joined `Flow` is actually considered a failure.  For instance, if Circuit Breaker is joined with an Akka HTTP flow, a `Success` Http Response with status code 500 internal server error should be considered a failure.

##### Scala

Scala API takes an `Option` of function: `Option[((Try[Out], Context)) => Boolean]`.  Below is an example where `Success("b")` is considered a failure:

```scala
def failureDecider(elem: (Try[String], UUID)): Boolean = elem match {
  case (Success("b"), _) => true
  case _ => false
}

CircuitBreakerBidiFlow[String, String, UUID](
  circuitBreakerState,
  fallback = None,
  failureDecider = Some(failureDecider))
```

##### Java

Java API takes an `Optional` of `Optional<Function<Pair<Try<Out>, Context>, Boolean>>`.  Below is an example where `Success("b")` is considered a failure:

```java
CircuitBreakerBidiFlow.create(
        circuitBreakerState,
        Optional.empty(),
        Optional.of(pair -> pair.first().get().equals("b")));
```

#### Creating CircuitBreakerState from system configuration

While `AtomicCircuitBreakerState` has programmatic API for configuration, it also allows the configuration to be provided through the system configuration:

```
sample-circuit-breaker {
  type = squbs.circuitbreaker
  max-failures = 1
  call-timeout = 50 ms
  reset-timeout = 20 ms
}
```

To initialize from configuration in Scala:

```scala
val circuitBreakerState = AtomicCircuitBreakerState("sample-circuit-breaker")
```

To initialize from configuration in Java:
```java
final CircuitBreakerState circuitBreakerState =
                AtomicCircuitBreakerState.create("sample-circuit-breaker", system);
```

#### Circuit Breaker across materializations

Please note, in many scenarios, the same circuit breaker instance is used across multiple materializations of the same flow.  For such scenarios, make sure to use a `CircuitBreakerState` instance that can be modified concurrently.  The default implementation `AtomicCircuitBreakerState` uses `Atomic` variables and can be used across multiple materializations.  More implementations, e.g., `Agent` based, can be introduced in the future.

#### Context to Unique Id Mapping

`Context` itself might be used as a unique id.  However, in many scenarios, `Context` contains more than the unique id itself or the unique id might be retrieved as a mapping from the `Context`.  squbs allows different options to provide a unique id:

   * `Context` itself is a type that can be used as a unique id, e.g., `Int`, `Long`, `java.util.UUID`
   * `Context` extends `UniqueId.Provider` and implements `def uniqueId`
   * `Context` is wrapped with `UniqueId.Envelope`
   * `Context` is mapped to a unique id by calling a function


With the first three options, a unique id can be retrieved directly through the context.

For the last option, `CircuitBreakerBidiFlow` allows a function to be passed in as a parameter.

##### Scala

The following API is used to pass a uniqueId mapper:

```scala
CircuitBreakerBidiFlow[In, Out, Context](
  circuitBreakerState: CircuitBreakerState,
  fallback: Option[((In, Context)) => (Try[Out], Context)],
  failureDecider: Option[((Try[Out], Context)) => Boolean],
  uniqueIdMapper: Context => Option[Any])
```
Here is an example with a uniqueId mapper:

```scala
case class MyContext(s: String, uuid: UUID)

val circuitBreakerState = AtomicCircuitBreakerState("sample", system.scheduler, 2, 20 milliseconds, 10 milliseconds)
val circuitBreakerBidiFlow = CircuitBreakerBidiFlow[String, String, MyContext](
  circuitBreakerState,
  None,
  None,
  (context: MyContext) => Some(context.id))

val flow = Flow[(String, MyContext)].mapAsyncUnordered(10) { elem =>
  (ref ? elem).mapTo[(String, MyContext)]
}

Source("a" :: "b" :: "b" :: "a" :: Nil)
  .map { s => (s, MyContext("dummy", UUID.randomUUID())) }
  .via(circuitBreakerBidiFlow.join(flow))
  .runWith(Sink.seq)
```

##### Java

The following API is used to pass a uniqueId mapper:

```java
public class CircuitBreakerBidiFlow {
	public static <In, Out, Context> BidiFlow<Pair<In, Context>, 
	                                          Pair<In, Context>,
	                                          Pair<Out, Context>,
	                                          Pair<Try<Out>, Context>,
	                                          NotUsed>
	create(CircuitBreakerState circuitBreakerState,
           Optional<Function<Pair<In, Context>, Pair<Try<Out>, Context>>> fallback,
           Optional<Function<Pair<Try<Out>, Context>, Boolean>> failureDecider,
           Function<Context, Optional<Object>> uniqueIdMapper);
}
```

Here is an example with a uniqueId mapper:

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
}

final CircuitBreakerState circuitBreakerState =
        AtomicCircuitBreakerState.create(
                "sample",
                system.scheduler(),
                2,
                FiniteDuration.apply(20, TimeUnit.MILLISECONDS),
                FiniteDuration.apply(10, TimeUnit.MILLISECONDS),
                system.dispatcher());

final BidiFlow<Pair<String, MyContext>,
               Pair<String, MyContext>,
               Pair<String, MyContext>,
               Pair<Try<String>, MyContext>,
               NotUsed>
        circuitBreakerBidiFlow =
        CircuitBreakerBidiFlow.create(
                circuitBreakerState,
                Optional.empty(),
                Optional.empty(),
                context -> Optional.of(context.id));

final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
        Flow.<Pair<String, MyContext>>create()
                .mapAsyncUnordered(3, elem -> ask(ref, elem, 5000))
                .map(elem -> (Pair<String, MyContext>)elem);

Source.from(Arrays.asList("a", "b", "b", "a"))
        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
        .via(circuitBreakerBidiFlow.join(flow))
        .runWith(Sink.seq(), mat);
```

#### Notifications

An `ActorRef` can be subscribed to receive all `TransitionEvents` or any transition event that it is intered in, e.g., `Closed`, `Open`, `HalfOpen`.

##### Scala

Here is an example that registers an `ActorRef` to receive events when circuit transitions to `Open` state:

```scala
circuitBreakerState.subscribe(self, Open)
```

##### Java

Here is an example that registers an `ActorRef` to receive events when circuit transitions to `Open` state:

```java
circuitBreakerState.subscribe(getRef(), Open.instance());
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
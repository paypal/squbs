# Timeout stage

### Overview

Some stream use cases may require each message in a flow to be processed within a bounded time or to send a timeout failure message instead.  squbs introduces `TimeoutBidi` Akka Streams stage to add timeout functionality to streams.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The timeout functionality is provided as a `BidiFlow` that can be connected to a flow via the `join` operator.  The timeout `BidiFlow` sends a `Try` to downstream:

   * If an output `msg` is provided by the wrapped flow within the timeout, then `Success(msg)` is passed down.
   * Otherwise, a `Failure(FlowTimeoutException())` is pushed to downstream.  


If there is no downstream demand, then no timeout message will be pushed down.  Moreover, no timeout check will be done unless there is downstream demand.  Accordingly, based on the demand scenarios, some messages might be send as a `Success` even after the timeout duration.

Timeout precision is at best 10ms to avoid unnecessary timer scheduling cycles.

The timeout feature is supported for flows that guarantee message ordering as well as for the ones that do not guarantee message ordering.  For the flows that do not guarantee message ordering, a `context` needs to be carried by the wrapped flow to uniquely identify each element.  While the usage of the `BidiFlow` is same for both types of flows, the creation of the `BidiFlow` is done via two different APIs.    

#### Flows with message order guarantee

`TimeoutBidiFlowOrdered` is used to create a timeout `BidiFlow` to wrap flows that do keep the order of messages.  Please note, with the message order guarantee, there can be a head-of-line blocking scenario, a slower element might cause subsequent elements to timeout.

##### Scala

```scala
val timeoutBidiFlow = TimeoutBidiFlowOrdered[String, String](20 milliseconds)
val flow = Flow[String].map(s => findAnEnglishWordThatStartWith(s))
Source("a" :: "b" :: "c" :: Nil)
  .via(timeoutBidiFlow.join(flow))
  .runWith(Sink.seq)
```      

##### Java

```java
final BidiFlow<String, String, String, Try<String>, NotUsed> timeoutBidiFlow =
    TimeoutBidiFlowOrdered.create(timeout);

final Flow<String, String, NotUsed> flow =
    Flow.<String>create().map(s -> findAnEnglishWordThatStartWith(s));
        
Source.from(Arrays.asList("a", "b", "c"))
    .via(timeoutBidiFlow.join(flow))
    .runWith(Sink.seq(), mat);
```   

#### Flows without message order guarantee

`TimeoutBidiFlowUnordered` is used to create a timeout `BidiFlow` to wrap flows that do not guarantee the order of messages.  To uniquely identify each element and its corresponding timing marks, a `context`, of any type defined by the application, needs to be carried around by the wrapped flow.  The requirement is that either the `Context` itself or a mapping from `Context` should be able to uniquely identify an element (see [Context to Unique Id Mapping](#context-to-unique-id-mapping) section for more details).

##### Scala

`TimeoutBidiFlowUnordered[In, Out, Context](timeout: FiniteDuration)` is used to create an unordered timeout `BidiFlow`.  This `BidiFlow` can be joined with any flow that takes in a `(In, Context)` and outputs a `(Out, Context)`.

```scala   
val timeoutBidiFlow = TimeoutBidiFlowUnordered[String, String, UUID](timeout) 
val flow = Flow[(String, UUID)].mapAsyncUnordered(10) { elem =>
  (ref ? elem).mapTo[(String, UUID)]
}

Source("a" :: "b" :: "c" :: Nil)
  .map { s => (s, UUID.randomUUID()) }
  .via(timeoutBidiFlow.join(flow))
  .runWith(Sink.seq)
```

##### Java

The following API is used to create an unordered timeout `BidiFlow`:

```java
public class TimeoutBidiFlowUnordered {
	public static <In, Out, Context> BidiFlow<Pair<In, Context>, 
	                                          Pair<In, Context>,
	                                          Pair<Out, Context>,
	                                          Pair<Try<Out>, Context>,
	                                          NotUsed>
	create(FiniteDuration timeout);
}
```

This `BidiFlow` can be joined with any flow that takes in a `akka.japi.Pair<In, Context>` and outputs a `akka.japi.Pair<Out, Context>`.

```java
final BidiFlow<Pair<String, UUID>, Pair<String, UUID>, Pair<String, UUID>, Pair<Try<String>, UUID>, NotUsed> timeoutBidiFlow =
        TimeoutBidiFlowUnordered.create(timeout);    
final Flow<Pair<String, UUID>, Pair<String, UUID>, NotUsed> flow =
        Flow.<Pair<String, UUID>>create()
                .mapAsyncUnordered(10, elem -> ask(ref, elem, 5000))
                .map(elem -> (Pair<String, UUID>)elem);

Source.from(Arrays.asList("a", "b", "c"))
        .map(s -> new Pair<>(s, UUID.randomUUID()))
        .via(timeoutBidiFlow.join(flow))
        .runWith(Sink.seq(), mat);    
```

##### Context to Unique Id Mapping

`Context` itself might be used as a unique id.  However, in many scenarios, `Context` contains more than the unique id itself or the unique id might be retrieved as a mapping from the `Context`.  squbs allows different options to provide a unique id:

   * `Context` itself is a type that can be used as a unique id, e.g., `Int`, `Long`, `java.util.UUID`
   * `Context` extends `UniqueId.Provider` and implements `def uniqueId`
   * `Context` is wrapped with `UniqueId.Envelope`
   * `Context` is mapped to a unique id by calling a function


With the first three options, a unique id can be retrieved directly through the context.

For the last option, `TimeoutBidiFlowUnordered` allows a function to be passed in as a parameter.


###### Scala

The following API is used to pass a uniqueId mapper:

```scala
TimeoutBidiFlowUnordered[In, Out, Context](timeout: FiniteDuration,
                                           uniqueIdMapper: Context => Option[Any])
```
  
  This `BidiFlow` can be joined with any flow that takes in a `(In, Context)` and outputs a `(Out, Context)`.

```scala
case class MyContext(s: String, uuid: UUID)

val timeoutBidiFlow = TimeoutBidiFlowUnordered[String, String, MyContext](timeout,
                                                                         (context: MyContext) => Some(context.uuid))
val flow = Flow[(String, MyContext)].mapAsyncUnordered(10) { elem =>
  (ref ? elem).mapTo[(String, MyContext)]
}

Source("a" :: "b" :: "c" :: Nil)
  .map( _ -> MyContext("dummy", UUID.randomUUID))
  .via(timeoutBidiFlow.join(flow))
  .runWith(Sink.seq) 
```

###### Java

The following API is used to pass a uniqueId mapper:

```java
public class TimeoutBidiFlowUnordered {
	public static <In, Out, Context> BidiFlow<Pair<In, Context>, 
	                                          Pair<In, Context>,
	                                          Pair<Out, Context>,
	                                          Pair<Try<Out>, Context>,
	                                          NotUsed>
	create(FiniteDuration timeout, Function<Context, Optional<Object>> uniqueIdMapper);
}
```

This `BidiFlow` can be joined with any flow that takes in a `akka.japi.Pair<In, Context>` and outputs a `akka.japi.Pair<Out, Context>`.

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

final BidiFlow<Pair<String, MyContext>,
               Pair<String, MyContext>,
               Pair<String, MyContext>,
               Pair<Try<String>, MyContext>,
               NotUsed>
    timeoutBidiFlow = TimeoutBidiFlowUnordered.create(timeout, context -> Optional.of(context.uuid));

final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
        Flow.<Pair<String, MyContext>>create()
                .mapAsyncUnordered(10, elem -> ask(ref, elem, 5000))
                .map(elem -> (Pair<String, MyContext>)elem);

Source.from(Arrays.asList("a", "b", "c"))
        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
        .via(timeoutBidiFlow.join(flow))
        .runWith(Sink.seq(), mat);
```

##### Clean up callback

The `TimeoutBidi` also provides a clean up callback function to be passed in. This function will be called for emitted elements that were already considered timed out.

An example use case for this functionality is when `TimeoutBidi` is used with Akka Http client. As described in [Implications of the streaming nature of Request/Response Entities](http://doc.akka.io/docs/akka-http/current/scala/http/implications-of-streaming-http-entity.html), all http responses must be consumed or discarded. By passing a clean up callback to discard the timed out requests when they complete we avoid clogging down the stream.

###### Scala
```scala
val akkaHttpDiscard = (response: HttpResponse) => response.discardEntityBytes()

TimeoutBidiFlowUnordered[HttpRequest, HttpResponse](20 milliseconds, cleanUp = akkaHttpDiscard)
```

###### Java
```java
final Consumer<HttpResponse> akkaHttpDiscard = httpResponse -> httpResponse.discardEntityBytes(materializer);
final FiniteDuration timeout = FiniteDuration.create(20, TimeUnit.MILLISECONDS);
final BidiFlow<Pair<HttpRequest, UUID>, 
               Pair<HttpRequest, UUID>, 
               Pair<HttpResponse, UUID>, 
               Pair<Try<HttpResponse>, UUID>, 
               NotUsed> 
    timeoutBidiFlow = TimeoutBidiFlowUnordered.create(timeout, akkaHttpDiscard);
```

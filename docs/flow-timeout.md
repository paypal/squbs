# Timeout Flow

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

`TimeoutBidiFlowUnordered` is used to create a timeout `BidiFlow` to wrap flows that do not guarantee the order of messages.  To uniquely identify each element and its corresponding timing marks, a `context`, of any type defined by the application, needs to be carried around by the wrapped flow.  The requirement is that either the `context` itself or an attribute accessed via the `context` should be able to uniquely identify an element.  By default, the `context` itself is used to uniquely identify an element (e.g., `context` is of type `UUID` or `Long`); however, the unique id accessor can be customized (e.g., the `context` is a `case class` with a field `val id: UUID`), please see  [Customizing unique id retriever](#customizing-unique-id-retriever) section.

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

`TimeoutBidiFlowUnordered.create[In, Out, Context](timeout: FiniteDuration)` is used to create an unordered timeout `BidiFlow`.  This `BidiFlow` can be joined with any flow that takes in a `akka.japi.Pair[In, Context]` and outputs a `akka.japi.Pair[Out, Context]`.

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

##### Customizing unique id retriever

There may be scenarios where the `context` contains more than the unique id itself or the unique id might be calculated as a function of the `context`.  Accordingly, `TimeoutBidiFlowUnordered` allows a function from `context` to `id` to be passed in as a parameter.

###### Scala

`TimeoutBidiFlowUnordered[In, Out, Context, Id](timeout: FiniteDuration, uniqueId: Context => Id)` is used to create an unordered timeout `BidiFlow` with a unique id retriever function.  This `BidiFlow` can be joined with any flow that takes in a `(In, Context)` and outputs a `(Out, Context)`.

```scala
case class MyContext(s: String, uuid: UUID)

val timeoutBidiFlow = TimeoutBidiFlowUnordered[String, String, MyContext, UUID](timeout, (mc: MyContext) => mc.uuid)
val flow = Flow[(String, MyContext)].mapAsyncUnordered(10) { elem =>
  (ref ? elem).mapTo[(String, MyContext)]
}

Source("a" :: "b" :: "c" :: Nil)
  .map { s => (s, MyContext("dummy", UUID.randomUUID())) }
  .via(timeoutBidiFlow.join(flow))
  .runWith(Sink.seq)  
```

###### Java

```java
TimeoutBidiFlowUnordered
    .create[In, Out, Context, Id]
    (timeout: FiniteDuration, uniqueId: java.util.function.Function[Context, Id])
``` 
is used to create an unordered timeout `BidiFlow` with a unique id retriever function.  This `BidiFlow` can be joined with any flow that takes in a `akka.japi.Pair[In, Context]` and outputs a `akka.japi.Pair[Out, Context]`.

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

final BidiFlow<Pair<String, MyContext>, Pair<String, MyContext>, Pair<String, MyContext>, Pair<Try<String>, MyContext>, NotUsed> timeoutBidiFlow =
        TimeoutBidiFlowUnordered.create(timeout, MyContext::uuid);
final Flow<Pair<String, MyContext>, Pair<String, MyContext>, NotUsed> flow =
        Flow.<Pair<String, MyContext>>create()
                .mapAsyncUnordered(10, elem -> ask(ref, elem, 5000))
                .map(elem -> (Pair<String, MyContext>)elem);

Source.from(Arrays.asList("a", "b", "c"))
        .map(s -> new Pair<>(s, new MyContext("dummy", UUID.randomUUID())))
        .via(timeoutBidiFlow.join(flow))
        .runWith(Sink.seq(), mat);
```
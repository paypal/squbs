# Perpetual Stream

### Overview

The `PerpetualStream` allows declaration of a stream that would start when the server starts and stop gracefully without dropping messages when the server stops. It is commonly used for message consumers from Kafka or JMS, but also used as a consolidation point for data from multiple streams received through HTTP requests.

`PerpetualStream` can be customized in various ways to fit your streams' needs. Those are discussed in the sections listed below:

* [Basic Use](#basic-use)
* [Override Lifecycle State to run the stream](#override-lifecycle-state-to-run-the-stream)
* [Shutdown Overrides](#shutdown-overrides)
* [Kill Switch Overrides](#kill-switch-overrides)
* [Receiving and forwarding a message to the stream](#receiving-and-forwarding-a-message-to-the-stream)
* [Handling Stream Errors](#handling-stream-errors)
* [Connecting a Perpetual Stream with an HTTP Flow](#connecting-a-perpetual-stream-with-an-http-flow)

### Dependency

`PerpetualStream` is part of core squbs. In general, you do not need to add an extra dependency. The classes are part of the following dependency:

```scala
"org.squbs" %% "squbs-unicomplex" % squbsVersion
```

### Usage

The `PerpetualStream` gets exposed as the `PerpetualStream` trait for Scala and `AbstractPerpertualStream` abstract class for Java. For brevity, we'll refer to both as `PerpetualStream`.

#### Basic Use

##### Scala

Streams making use of `PerpetualStream` will want to materialize to certain known types, allowing the hooks in `PerpetualStream` to work seamlessly with minimal amount of custom overrides. The options are:

* Materialize to a `Future[_]`, meaning a future of any type. In this case the shared `killSwitch` from `PerpetualStream` should be embedded or `shutdown()` would need to be overridden.
* Materialize to a `(KillSwitch, Future[_])` tuple. The `KillSwitch` will be used for initiating the shutdown of the stream.
* Materialize to a `List` or any `Product` (`List`s, `Tuple`s are all subtypes of `Product`) where the first element is a `KillSwitch` and the last element is a `Future`.

Streams with different materialized values can still be used but `shutdown()` needs to be overridden.

Common examples for well behaved streams can be seen below:

```scala
class WellBehavedStream extends PerpetualStream[Future[Done]] {

  def generator = Iterator.iterate(0) { p => 
    if (p == Int.MaxValue) 0 else p + 1 
  }

  val source = Source.fromIterator(generator _)

  val ignoreSink = Sink.ignore
  
  override def streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(ignoreSink) {
    implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        source ~> killSwitch.flow[Int] ~> sink
        ClosedShape
  })
}
```

Alternatively, the following code shows another conformant `PerpetualStream` materializing its first element as `KillSwitch`:

```scala
class WellBehavedStream2 extends PerpetualStream[(KillSwitch, Future[Done])] {

  def generator = Iterator.iterate(0) { p => 
    if (p == Int.MaxValue) 0 else p + 1 
  }

  val source = Source.fromIterator(generator _)

  val ignoreSink = Sink.ignore
  
  override def streamGraph = RunnableGraph.fromGraph(
    GraphDSL.createGraph(KillSwitch.single[Int], ignoreSink)((_,_)) { implicit builder =>
      (kill, sink) =>
        import GraphDSL.Implicits._
        source ~> kill ~> sink
        ClosedShape
  })
}
```

That's it. These streams are well behaved because they materialize to the sink's materialized value, which is a `Future[Done]` in the first example, or a `(KillSwitch, Future[Done])` in the second one.

##### Java

Streams making use of `AbstractPerpetualStream` will want to materialize to certain known types, allowing the hooks in `AbstractPerpetualStream` to work seamlessly with minimal amount of custom overrides. The options are:

* Materialize to a `CompletionStage<?>`, meaning a Java `CompletionStage` of any type. In this case the shared `killSwitch` from `AbstractPerpetualStream` should be embedded or `shutdown()` would need to be overridden.
* Materialize to a `Pair<KillSwitch, CompletionStage<?>>`. The `KillSwitch` will be used for initiating the shutdown of the stream.
* Materialize to a `java.util.List` where the first element is a `KillSwitch` and the last element is a `CompletionStage`.

Streams with different materialized values can still be used but `shutdown()` needs to be overridden.

Common examples for well behaved streams can be seen below:

```java
public class WellBehavedStream extends AbstractPerpetualStream<CompletionStage<Done>> {

    Sink<Integer, CompletionStage<Done>> ignoreSink = Sink.ignore();
  
    @Override
    public RunnableGraph<CompletionStage<Done>>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(ignoreSink, (builder, sink) -> {
            SourceShape<Integer> source = builder.add(
                    Source.unfold(0, i -> {
                        if (i == Integer.MAX_VALUE) {
                            return Optional.of(Pair.create(0, i));
                        } else {
                            return Optional.of(Pair.create(i + 1, i));
                        }
                    })
            );
                        
            FlowShape<Integer, Integer> killSwitch= builder.add(killSwitch().<Integer>flow());

            builder.from(source).via(killSwitch).to(sink);
            
            return ClosedShape.getInstance();
        }));
    }
```

Alternatively, the following code shows another conformant `PerpetualStream` materializing its first element as `KillSwitch`:

```java
public class WellBehavedStream2 extends
        AbstractPerpetualStream<Pair<KillSwitch, CompletionStage<Done>>> {

    Sink<Integer, CompletionStage<Done>> ignoreSink = Sink.ignore();
  
    @Override
    public RunnableGraph<Pair<KillSwitch, CompletionStage<Done>>>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(KillSwitches.<Integer>single(),
            ignoreSink, Pair::create, (builder, kill, sink) -> {
                SourceShape<Integer> source = builder.add(
                        Source.unfold(0, i -> {
                            if (i == Integer.MAX_VALUE) {
                                return Optional.of(Pair.create(0, i));
                            } else {
                                return Optional.of(Pair.create(i + 1, i));
                            }
                        })
                );

                builder.from(source).via(kill).to(sink);
            
                return ClosedShape.getInstance();
            }));
    }
```

That's it. These streams are well behaved because they materialize to the sink's materialized value, which is a `CompletionStage<Done>` in the first example, or a `Pair<KillSwitch, CompletionStage<Done>>` in the second one.

#### Override Lifecycle State to run the stream

There may be scenarios where a stream need to be materialized at a different lifecycle than `active`. In such scenarios, override `streamRunLifecycleState`, e.g.,:

##### Scala

```scala
override lazy val streamRunLifecycleState: LifecycleState = Initializing
```

##### Java

```java
@Override
public LifecycleState streamRunLifecycleState() {
    return Initializing.instance();
}
```

#### Shutdown Overrides
It is sometimes not possible to define a well behaved stream. For instance, the `Sink` may not materialize to a `Future` or `CompletionStage` or you need to do further cleanup at shutdown. For this reason, it is possible to override `shutdown` as in the following code:

##### Scala

```scala
override def shutdown(): Future[Done] = {
  // Do all your cleanup
  // For safety, call super
  super.shutdown()
  // The Future from super.shutdown may not mean anything.
  // Feel free to create your own future that identifies the
  // stream being done. Return your Future instead.
}
```

##### Java

```java
@Override
public CompletionStage<Done> shutdown() {
    // Do all your cleanup
    // For safety, call super
    super.shutdown();
    // The Future from super.shutdown may not mean anything.
    // Feel free to create your own future that identifies the
    // stream being done. Return your Future instead.
}
```

`shutdown` needs to do the following:

1. Initiate the shutdown of the stream.
2. Do any other cleanup.
3. Return the future that completes when the stream has finished processing.

Note: It is always advisable to call `super.shutdown`. There is no harm or other side-effect in making this call.

##### Alternate Shutdown Mechanisms
The `source` may not materialize to `KillSwitch` and provide a better way to do a proper shutdown than using the `killSwitch`. Just use the shutdown mechanism of the `source` in such cases and override `shutdown` to initiate the shutdown of the source. The `killSwitch` remains unused.

#### Kill Switch Overrides
If the `killSwitch` needs to be shared across multiple streams, you can override `killSwitch` to reflect the shared instance.

##### Scala

```scala
override lazy val killSwitch = mySharedKillSwitch
```

##### Java

```java
@Override
public SharedKillSwitch killSwitch() {
    return KillSwitches.shared("myKillSwitch");
}
```

#### Receiving and forwarding a message to the stream
Some streams take input from actor messages. While it is possible for some stream configurations to materialize to the `ActorRef` of the source, it is difficult to address this actor. Since `PerpetualStream` itself is an actor, it can have a well known address/path and forward to message to the stream source. To do so, we need to override the `receive` or `createReceive()` as follows:

##### Scala

```scala
override def receive = {
  case msg: MyStreamMessage =>
    val (sourceActorRef, _) = matValue
    sourceActorRef forward msg
}
```

##### Java

```java
@Override
public Receive createReceive() {
    return receiveBuilder()
            .match(MyStreamMessage.class, msg -> {
                ActorRef sourceActorRef = matValue().first();
                sourceActorRef.forward(msg, getContext());
            })
            .build();
}
```

#### Handling Stream Errors
The `PerpetualStream` default behavior resumes on errors uncaught by the stream stages. The message causing the error is ignored. Override `decider` if a different behavior is desired.

##### Scala

```scala
override def decider: Supervision.Decider = { t => 
  log.error("Uncaught error {} from stream", t)
  t.printStackTrace()
  Restart
}
```

##### Java

```java
@Override
public org.apache.pekko.japi.function.Function<Throwable, Supervision.Directive> decider() {
    return t -> {
        log().error("Uncaught error {} from stream", t);
        t.printStackTrace();
        return Supervision.restart();
    };
}
```

`Restart` will restart the stage that has an error without shutting down the stream. Please see [Supervision Strategies](http://doc.pekko.io/docs/pekko/current/scala/stream/stream-error.html#Supervision_Strategies) for possible strategies.

#### Connecting a Perpetual Stream with an HTTP Flow

pekko HTTP allows defining a `Flow[HttpRequest, HttpResponse, NotUsed]`, which gets materialized for each http connection.  There are scenarios where an app needs to connect the http flow to a long running stream that needs to be materialized only once (e.g., publishing to Kafka).  pekko HTTP enables end-to-end streaming in such scenarios with [`MergeHub`](http://doc.pekko.io/docs/pekko/current/scala/stream/stream-dynamic.html#dynamic-fan-in-and-fan-out-with-mergehub-broadcasthub-and-partitionhub).  squbs provides utilities to connect an http flow with a `PerpetualStream` that uses `MergeHub`.  


Below are sample `PerpetualStream` implementations - two Scala and two Java equivalents, all using `MergeHub`.
Type parameter `Sink[MyMessage, NotUsed]` describes the inlet of the `RunnableGraph` instance that will be used as a destination (`Sink`) by the http flow part in `HttpFlowWithMergeHub` further down.
First a simplest outline of the logic:

##### Scala

```scala
class PerpetualStreamWithMergeHub extends PerpetualStream[Sink[MyMessage, NotUsed]] {

  override lazy val streamRunLifecycleState: LifecycleState = Initializing
  

  /**
    * Describe your graph by implementing streamGraph
    *
    * @return The graph.
    */
  override def streamGraph= MergeHub.source[MyMessage].to(Sink.ignore)
}
```

##### Java

```java
public class PerpetualStreamWithMergeHub extends AbstractPerpetualStream<Sink<MyMessage, NotUsed>> {

    @Override
    public LifecycleState streamRunLifecycleState() {
        return Initializing.instance();
    }

    /**
     * Describe your graph by implementing streamGraph
     *
     * @return The graph.
     */
    @Override
    public RunnableGraph<Sink<MyMessage, NotUsed>> streamGraph() {
        return MergeHub.of(MyMessage.class).to(Sink.ignore());
    }
}
```

From outside prospective (by http flow) this class is seen as terminal `Sink[MyMessage, NotUsed]`, which means that
`PerpetualStreamWithMergeHub` expects to receive `MyMessage` on its inlet and will not emit anything out, i.e. its outlet is plugged. 
From the inside prospective `MergeHub` is the source of `MyMessage`s. Those messages are passed to `Sink.ignore`, which is nothing.
`MergeHub.source[MyMessage]` produces runtime instance, with inlet of type `Sink[MyMessage, NotUsed]`, which conforms to `PerpetualStream[Sink[MyMessage, NotUsed]]` type parameter.
The `.to(Sink.ignore)` completes or "closes" this `Shape` with a plugged outlet. End result is an instance of `RunnableGraph[Sink[MyMessage, NotUsed]]` 


A bit more involved example using GraphDSL:

##### Scala

```scala
final case class MyMessage(ip:String, ts:Long)
final case class MyMessageEnrich(ip:String, ts:Long, enrichTs:List[Long])

class PerpetualStreamWithMergeHub extends PerpetualStream[Sink[MyMessage, NotUsed]]  {

  override lazy val streamRunLifecycleState: LifecycleState = Initializing
  
  // inlet - destination for MyMessage messages
  val source = MergeHub.source[MyMessage]
 
  //outlet - discard messages
  val sink = Sink.ignore
  
  //flow component, which supposedly does something to MyMessage
  val preprocess = Flow[MyMessage].map{inMsg =>
      val outMsg = MyMessageEnrich(ip=inMsg.ip, ts = inMsg.ts, enrichTs = List.empty[Long])
      println(s"Message inside stream=$inMsg")
      outMsg
  }
  
    // building a flow based on another flow, to do some dummy enrichment
  val enrichment = Flow[MyMessageEnrich].map{inMsg=>
      val outMsg = MyMessageEnrich(ip=inMsg.ip.replaceAll("\\.","-"), ts = inMsg.ts, enrichTs = System.currentTimeMillis()::inMsg.enrichTs)
      println(s"Enriched Message inside enrich step=$outMsg")
      outMsg
  }
    

  /**
    * Describe your graph by implementing streamGraph
    *
    * @return The graph.
    */
  override def streamGraph: RunnableGraph[Sink[MyMessage, NotUsed]] = RunnableGraph.fromGraph(
    GraphDSL.createGraph(source) { implicit builder=>
        input =>  
          import GraphDSL.Implicits._
            
          input ~> killSwitch.flow[MyMessage] ~> preprocess ~> enrichment ~> sink
          
          ClosedShape
      })
}
```

##### Java

```java
public class PerpetualStreamWithMergeHub extends AbstractPerpetualStream<Sink<MyMessage, NotUsed>> {

    // inlet - destination for MyMessage messages
    Source<MyMessage, Sink<MyMessage, NotUsed>> source = MergeHub.of(MyMessage.class);

    @Override
    public LifecycleState streamRunLifecycleState() {
        return Initializing.instance();
    }

    /**
     * Describe your graph by implementing streamGraph
     *
     * @return The graph.
     */
    @Override
    public RunnableGraph<Sink<MyMessage, NotUsed>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(source, (builder, input) -> {
            
            FlowShape<MyMessage, MyMessage> killSwitch = builder.add(killSwitch().<MyMessage>flow());

            //flow component, which supposedly does something to MyMessage
            FlowShape<MyMessage, MyMessageEnrich> preProcess = builder.add(Flow.<MyMessage>create().map(inMsg -> {
                MyMessageEnrich outMsg = new MyMessageEnrich(inMsg.ip, inMsg.ts, new ArrayList<>());
                System.out.println("Message inside stream=" + inMsg);
                return outMsg;
            }));

            // building a flow based on another flow, to do some dummy enrichment
            FlowShape<MyMessageEnrich, MyMessageEnrich> enrichment =
                    builder.add(Flow.<MyMessageEnrich>create().map(inMsg -> {
                        inMsg.enrichTs.add(System.currentTimeMillis());
                        MyMessageEnrich outMsg = new MyMessageEnrich(inMsg.ip.replaceAll("\\.","-"),
                                inMsg.ts, inMsg.enrichTs);
                        System.out.println("Enriched Message inside enrich step=" + outMsg);
                        return outMsg;
                    }));

            //outlet - discard messages
            SinkShape<Object> sink = builder.add(Sink.ignore());

            builder.from(input).via(killSwitch).via(preProcess).via(enrichment).to(sink);

            return ClosedShape.getInstance();
        }));
    }
}
```

Let's see how all parts are falling into a place:
`streamGraph` is expected to return `RunnableGraph` with the same type parameter as described in `PerpetualStream[Sink[MyMessage, NotUsed]]`.
Our `source` is a `MergeHub`, it is expected to receive a `MyMessage`, which makes its materialized (runtime) type a `Sink[MyMessage,NotUsed]`.
Our graph is built starting with our `source`, by passing it as a parameter to `GraphDSL.create(s:Shape)` constructor. 
The result is an instance of `RunnableGraph[Sink[MyMessage, NotUsed]]`, which is a `ClosedShape` with `Sink` inlet and plugged outlet.

Potentially confusing part when looking at this example is mixing `Sink` and `source` names to refer to the same. It looks a bit strange in English.
Let's use outside vs. inside prospective explanation again:
From the outside prospective our component is seen as a `Sink[MyMessage, NotUsed]`. This is achieved by using `MergeHub`, which from the inside prospective is a source of the messages hence `val` name `source`.
Correspondingly, in the event we need to emit something out, our `val sink` will be actually some shape with outlet of type `Source[MyMessage, NotUsed]`.
 
 
Let's add the above `PerpetualStream` in `squbs-meta.conf`.  Please see [Well Known Actors](bootstrap.md#well-known-actors) for more details.

```
cube-name = org.squbs.stream.mycube
cube-version = "0.0.1"
squbs-services = [
  {
    class-name = org.squbs.stream.HttpFlowWithMergeHub
    web-context = mergehub
  }
]
squbs-actors = [
  {
    class-name = org.squbs.stream.PerpetualStreamWithMergeHub
    name = perpetualStreamWithMergeHub
  }
]
```

##### Scala

The HTTP `FlowDefinition` can be connected to the `PerpetualStream` as follows by extending `PerpetualStreamMatValue` and using `matValue` method.
Type parameter for the `PerpetualStreamMatValue` describes the data type flowing between the HTTP flow and the `MergeHub`.
(both versions of `PerpetualStreamWithMergeHub` above expect to receive `MyMessage`, i.e. both have inlet of a type `Sink[MyMessage, NotUsed]`).

```scala
class HttpFlowWithMergeHub extends FlowDefinition with PerpetualStreamMatValue[MyMessage] {

  override val flow: Flow[HttpRequest, HttpResponse, NotUsed] =
    Flow[HttpRequest]
      .mapAsync(1)(Unmarshal(_).to[MyMessage])
      .alsoTo(matValue("/user/mycube/perpetualStreamWithMergeHub"))
      .map { myMessage => HttpResponse(entity = s"Received Id: ${myMessage.id}") }
}
```

##### Java

The HTTP `FlowDefinition` can be connected to the `PerpetualStream` as follows by extending `FlowToPerpetualStream` instead of `FlowDefinition` directly. Note that `FlowToPerpetualStream` **is a** `FlowDefinition`. We use the `matValue` method as the sink to send HTTP messages to the `MergeHub` defined in the `PerpetualStream`.

```java
class HttpFlowWithMergeHub extends FlowToPerpetualStream {

    private final Materializer mat = Materializer.createMaterializer(context().system());
    private final MarshalUnmarshal mu = new MarshalUnmarshal(context().system().dispatcher(), mat);

    @Override
    public Flow<HttpRequest, HttpResponse, NotUsed> flow() {
        return Flow.<HttpRequest>create()
                .mapAsync(1, req -> mu.apply(unmarshaller(MyMessage.class), req.entity()))
                .alsoTo(matValue("/user/mycube/perpetualStreamWithMergeHub"))
                .map(myMessage -> HttpResponse.create().withEntity("Received Id: " + myMessage.ip));
    }
}
```

Let's see what's happening here:
`matValue` method finds the `RunnableGraph` component registered under the `/user/mycube/perpetualStreamWithMergeHub`.
This happens to be the bootstrapped instance of our `PerpetualStreamWithMergeHub`.
`alsoTo` expects result of `matValue` to be a `Sink` for `MyMessage`.
I.e. `Sink[MyMessage, NotUsed]`. And as we've seen above this is exactly what `PerpetualStreamWithMergeHub.streamGraph` will produce. 
(Remembering our two prospectives: here `alsoTo` looks at `PerpetualStreamWithMergeHub` from the outside prospective and sees a `Sink[MyMessage, NotUsed]`.)

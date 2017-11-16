# Streams Lifecycle
Akka Streams/Reactive stream needs to be integrated with the [Runtime Lifecycle](lifecycle.md) of the server. For this, an automated or semi-automated integration is provided through `PerpetualStream` Scala trait and `AbstractPerpetualStream` abstract class for Java. For fine-grained control over stream sources directly, `LifecycleManaged` provides a wrapping that can control any source component for proper stopping and shutdown so the flow can start/stop gracefully.

## PerpetualStream
The `PerpetualStream` allows declaration of a stream that would start when the server starts and stop gracefully without dropping messages when the server stops. It gets exposed as the `PerpetualStream` trait for Scala and `AbstractPerpertualStream` abstract class for Java. For brevity, we'll refer to both as `PerpetualStream`.

##### Scala

Streams making use of `PerpetualStream` will want to conform to the following requirements, which will allow the hooks in `PerpetualStream` to work seamlessly with minimal amount of custom overrides:

1. Have `killSwitch.flow` as the first stream processing stage after the source(s). `killSwitch` is a standard Akka `SharedKillSwitch` that is provided by the `PerpetualStream` trait.
2. The stream materializes to a `Future` or a `Product` with a `Future` as its last element. A `Product` is the super class for `Tuple`, `List`, and much more. It is natural for `Sink`s to materialize to a `Future`. If more materialized values are asked for, it usually comes in some form of a `Product`. The `Sink` being the last element in the stream is also commonly materialized to the last element of the `Product`.
3. This `Future` (materialized value or last element of the materialized value) represents the completion of the stream. In other words, this future is completed when the stream completes.

If all the requirements above are met, no other custom overrides are needed for `PerpetualStream` to function. The following code shows a fully conformant `PerpetualStream`:

```scala
class WellBehavedStream extends PerpetualStream[Future[Done]] {

  def generator = Iterator.iterate(0) { p => 
    if (p == Int.MaxValue) 0 else p + 1 
  }

  val source = Source.fromIterator(generator _)

  val ignoreSink = Sink.ignore
  
  override def streamGraph = RunnableGraph.fromGraph(GraphDSL.create(ignoreSink) {
    implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        source ~> killSwitch.flow[Int] ~> sink
        ClosedShape
  })
}
```

That's it. This stream is well behaved because it materializes to the sink's materialized value, which is a `Future[Done]`.

##### Java

Streams making use of `AbstractPerpetualStream` will want to conform to the following requirements, which will allow the hooks in `AbstractPerpetualStream` to work seamlessly with minimal amount of custom overrides:

1. Have `killSwitch().flow()` as the first stream processing stage after the source(s). `killSwitch` is a standard Akka `SharedKillSwitch` that is provided by `AbstractPerpetualStream`.
2. The stream materializes to a `CompletionStage` or a `Pair` or `List` with a `CompletionStage` as its last element. It is natural for `Sink`s to materialize to a `CompletionStage`. If more materialized values are asked for, it usually comes in some form of a `Pair` (for two), or a `List` (for multiple materialized values). The `Sink` being the last element in the stream is also commonly materialized to the last element of the `Pair` or `List`.
3. This `CompletionStage` (materialized value or last element of the materialized value) represents the completion of the stream. In other words, this `CompletionStage` is completed when the stream completes.

If all the requirements above are met, no other custom overrides are needed for `PerpetualStream` to function. The following code shows a fully conformant `PerpetualStream`:

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

That's it. This stream is well behaved because it materializes to the sink's materialized value, which is a `CompletionStage<Done>`.

### Override Lifecycle State to run the stream

There may be scenarios where a stream need to be materialized at a different lifecycle than `active`.  In such scenarios, override `streamRunLifecycleState`, e.g.,:

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

### Shutdown Overrides
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

### Alternate Shutdown Mechanisms
The `source` may provide a better way to do a proper shutdown than using the `killSwitch`. Just use the shutdown mechanism of the `source` in such cases and override `shutdown` to initiate the shutdown of the source. The `killSwitch` remains unused.

### Kill Switch Overrides
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

### Receiving and forwarding a message to the stream
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

### Handling Stream Errors
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
public akka.japi.function.Function<Throwable, Supervision.Directive> decider() {
    return t -> {
        log().error("Uncaught error {} from stream", t);
        t.printStackTrace();
        return Supervision.restart();
    };
}
```

`Restart` will restart the stage that has an error without shutting down the stream. Please see [Supervision Strategies](http://doc.akka.io/docs/akka/current/scala/stream/stream-error.html#Supervision_Strategies) for possible strategies.

### Putting It Together
The following examples makes many of the possible overrides discussed above.

##### Scala

```scala
class MsgReceivingStream extends PerpetualStream[(ActorRef, Future[Done])] {

  val actorSource = Source.actorPublisher[MyStreamMsg](Props[MyPublisher])
  val ignoreSink = Sink.ignore[MyStreamMsg]
  
  override def streamGraph = RunnableGraph.fromGraph(GraphDSL.create(actorSource, ignoreSink)((_, _)) {
    implicit builder =>
      (source, sink) =>
        import GraphDSL.Implicits._
        source ~> sink
        ClosedShape
  })
  
  // Just forward the message to the stream source
  override def receive = {
    case msg: MyStreamMsg =>
      val (sourceActorRef, _) = matValue
      sourceActorRef forward msg
  }
  
  override def shutdown() = {
    val (sourceActorRef, _) = matValue
    sourceActorRef ! cancelStream
    // Sink materialization conforms
    // so super.shutdown() will give the right future
    super.shutdown()
  }
}
```

##### Java

```java
public class MsgReceivingStream extends AbstractPerpetualStream<Pair<ActorRef, CompletionStage<Done>>> {

    Source<MyStreamMsg, ActorRef> actorSource = Source.actorPublisher(Props.create(MyPublisher.class));
    Sink<MyStreamMsg, CompletionStage<Done>> ignoreSink = Sink.ignore();

    @Override
    public RunnableGraph<Pair<ActorRef, CompletionStage<Done>>> streamGraph() {
        return RunnableGraph.fromGraph(GraphDSL.create(actorSource, ignoreSink, Pair::create,
                (builder, source, sink) -> {

                    builder.from(source).to(sink);

                    return ClosedShape.getInstance();
                }));
    }

    // Just forward the message to the stream source
    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(MyStreamMsg.class, msg -> {
                    ActorRef sourceActorRef = matValue().first();
                    sourceActorRef.forward(msg, getContext());
                })
                .build();
    }

    @Override
    public CompletionStage<Done> shutdown() {
        ActorRef sourceActorRef = matValue().first();
        sourceActorRef.tell(cancelStream() ,getSelf());
        // Sink materialization conforms
        // so super.shutdown() will give the right future
        return super.shutdown();
    }
}
```

## Connecting a Perpetual Stream with an HTTP Flow

Akka HTTP allows defining a `Flow[HttpRequest, HttpResponse, NotUsed]`, which gets materialized for each http connection.  There are scenarios where an app needs to connect the http flow to a long running stream that needs to be materialized only once (e.g., publishing to Kafka).  Akka HTTP enables end-to-end streaming in such scenarios with [`MergeHub`](http://doc.akka.io/docs/akka/current/scala/stream/stream-dynamic.html#dynamic-fan-in-and-fan-out-with-mergehub-broadcasthub-and-partitionhub).  squbs provides utilities to connect an http flow with a `PerpetualStream` that uses `MergeHub`.  


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
    GraphDSL.create(source) { implicit builder=>
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

    private final Materializer mat = ActorMaterializer.create(context().system());
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

## Making A Lifecycle-Sensitive Source
If you wish to have a source that is fully connected to the lifecycle events of squbs, you can wrap any source with `LifecycleManaged`.

**Scala**

```scala
val inSource = <your-original-source>
val aggregatedSource = LifecycleManaged().source(inSource)
```

**Java**

```java
final Source inSource = <your-original-source>
final Source aggregatedSource = new LifecycleManaged(system).source(inSource);
```

The resulting source will be an aggregated source materialize to a `(T, ActorRef)` where `T` is the materialized type of `inSource` and `ActorRef` is the materialized type of the trigger actor which receives events from the Unicomplex, the squbs container.

The aggregated source does not emit from original source until lifecycle becomes `Active`, and stop emitting element and shuts down the stream after lifecycle state becomes `Stopping`.

## Custom Aggregated Triggered Source
If you want your flow to enable/disable to custom events, you can integrate with a custom trigger source,
element `true` will enable, `false` will disable.

Note that `Trigger` takes an argument `eagerComplete` which defaults to `false` in Scala but has to be
passed in Java. If `eagerComplete` is set to `false`, completion and/or termination of the trigger source actor
will detach the trigger. If set to `true`, such termination will complete the stream.

##### Scala

```scala
import org.squbs.stream.TriggerEvent._

val inSource = <your-original-source>
val trigger = <your-custom-trigger-source>.collect {
  case 0 => DISABLE
  case 1 => ENABLE
}

val aggregatedSource = new Trigger().source(inSource, trigger)
```

##### Java

```java
import static org.squbs.stream.TriggerEvent.DISABLE;
import static org.squbs.stream.TriggerEvent.ENABLE;

final Source<?, ?> inSource = <your-original-source>;
final Source<?, ?> trigger = <your-custom-trigger-source>.collect(new PFBuilder<Integer, TriggerEvent>()
    .match(Integer.class, p -> p == 1, p -> ENABLE)
    .match(Integer.class, p -> p == 0, p -> DISABLE)
    .build());

final Source aggregatedSource = new Trigger(false).source(inSource, trigger);
```

## Custom Lifecycle Event(s) for Trigger
If you want to respond to more lifecycle events beyond `Active` and `Stopping`, for example you want `Failed` to also stop the flow, you can modify the lifecylce event mapping.

##### Scala

```scala
import org.squbs.stream.TriggerEvent._

val inSource = <your-original-source>
val trigger = Source.actorPublisher[LifecycleState](Props.create(classOf[UnicomplexActorPublisher]))
  .collect {
    case Active => ENABLE
    case Stopping | Failed => DISABLE
  }

val aggregatedSource = new Trigger().source(inSource, trigger)
```

##### Java

```java
import static org.squbs.stream.TriggerEvent.DISABLE;
import static org.squbs.stream.TriggerEvent.ENABLE;

final Source<?, ?> inSource = <your-original-source>;
final Source<?, ActorRef> trigger = Source.<LifecycleState>actorPublisher(Props.create(UnicomplexActorPublisher.class))
    .collect(new PFBuilder<Integer, TriggerEvent>()
        .matchEquals(Active.instance(), p -> ENABLE)
        .matchEquals(Stopping.instance(), p -> DISABLE)
        .matchEquals(Failed.instance(), p -> DISABLE)
        .build()
    );

final Source aggregatedSource = new Trigger(false).source(inSource, trigger);
```

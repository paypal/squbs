# Streams Lifecycle
Akka Streams/Reactive stream needs to be integrated with the [Runtime Lifecycle](lifecycle.md) of the server. For this, an automated or semi-automated integration is provided through `PerpetualStream`. For fine-grained control over stream sources directly, `LifecycleManaged` provides a wrapping that can control any source component for proper stopping and shutdown so the flow can start/stop gracefully.

## PerpetualStream
The `PerpetualStream` is a trait that allows declaration of a stream that would start when the server starts and stop gracefully without dropping messages when the server stops.

Streams making use of `PerpetualStream` will want to conform to the following requirements, which will allow the hooks in `PerpetualStream` to work seamlessly with minimal amount of custom overrides:

1. Have `killSwitch.flow` as the first stream processing stage after the source(s). `killSwitch` is a standard Akka `SharedKillSwitch` that is provided by the `PerpetualStream` trait.
2. The stream materializes to a `Future` or a `Product` with a `Future` as its last element. A `Product` is the super class for `Tuple`, `List`, and much more. It is natural for `Sink`s to materialize to a `Future`. If more materialized values are asked for, it usually comes in some form of a `Product`. The `Sink` being the last element in the stream is also commonly materialized to the last element of the `Product`.
3. This `Future` (materialized value or last element of the materialized value) represents the completion of the stream. In other words, this future is completed when the stream completes.

If all the requirements above are met, no other custom overrides are needed for `PerpetualStream` to function. The following code shows a fully conformant `PerpetualStream`

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

### Override Lifecycle State to run the stream

There may be scenarios where a stream need to be materialized at a different lifecycle than `active`.  In such scenarios, override `streamRunLifecycleState`, e.g.,:

```scala
override lazy val streamRunLifecycleState: LifecycleState = Initializing
```

### Shutdown Overrides
It is sometimes not possible to define a well behaved stream. For instance, the `Sink` may not materialize to a `Future` or you need to do further cleanup at shutdown. For this reason, it is possible to override `shutdown` as in the following code:

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

`shutdown` needs to do the following:

1. Initiate the shutdown of the stream.
2. Do any other cleanup.
3. Return the future that completes when the stream has finished processing.

Note: It is always advisable to call `super.shutdown`. There is no harm or other side-effect in making this call.

### Alternate Shutdown Mechanisms
The `source` may provide a better way to do a proper shutdown than using the `killSwitch`. Just use the shutdown mechanism of the `source` in such cases and override `shutdown` to initiate the shutdown of the source. The `killSwitch` remains unused.

### Kill Switch Overrides
If the `killSwitch` needs to be shared across multiple streams, you can override `killSwitch` to reflect the shared instance.

```scala
  override lazy val killSwitch = mySharedKillSwitch
```

### Receiving and forwarding a message to the stream
Some streams take input from actor messages. While it is possible for some stream configurations to materialize to the `ActorRef` of the source, it is difficult to address this actor. Since `PerpetualStream` itself is an actor, it can have a well known address/path and forward to message to the stream source. To do so, we need to override the `receive` as follows:

```scala
  override def receive = {
    case msg: MyStreamMessage =>
      val (sourceActorRef, _) = matValue
      sourceActorRef forward msg
  }
```

### Handling Stream Errors
The `PerpetualStream` default behavior resumes on errors uncaught by the stream stages. The message causing the error is ignored. Override `decider` if a different behavior is desired.

```scala
  override def decider: Supervision.Decider = { t => 
    log.error("Uncaught error {} from stream", t)
    t.printStackTrace()
    Restart
  }
```

`Restart` will restart the stage that has an error without shutting down the stream. Please see [Supervision Strategies](http://doc.akka.io/docs/akka/current/scala/stream/stream-error.html#Supervision_Strategies) for possible strategies.

### Putting It Together
The following example makes many of the possible overrides discussed above.

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

## Connecting a Perpetual Stream with an HTTP Flow

Akka HTTP allows defining a `Flow[HttpRequest, HttpResponse, NotUsed]`, which gets materialized for each http connection.  There are scenarios where an app needs to connect the http flow to a long running stream that needs to be materialized only once (e.g., publishing to Kafka).  Akka HTTP enables end-to-end streaming in such scenarios with [MergeHub](http://doc.akka.io/docs/akka/current/scala/stream/stream-dynamic.html#dynamic-fan-in-and-fan-out-with-mergehub-and-broadcasthub).  squbs provides utilities to easily connect an http flow with a `PerpetualStream` that uses `MergeHub`.  


Below is a sample `PerpetualStream` that uses `MergeHub`.

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

The HTTP `FlowDefinition` can be connected to the `PerpetualStream` as follows by extending `PerpetualStreamMatValue` and using `matValue` method:

```scala
class HttpFlowWithMergeHub extends FlowDefinition with PerpetualStreamMatValue[Sink[MyMessage, NotUsed]] {

  override val flow: Flow[HttpRequest, HttpResponse, NotUsed] =
    Flow[HttpRequest]
      .mapAsync(1)(Unmarshal(_).to[MyMessage])
      .alsoTo(matValue("/user/mycube/perpetualStreamWithMergeHub"))
      .map { myMessage => HttpResponse(entity = s"Received Id: ${myMessage.id}") }
}
```

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

**Scala**

```scala
import org.squbs.stream.TriggerEvent._

val inSource = <your-original-source>
val trigger = <your-custom-trigger-source>.collect {
  case 0 => DISABLE
  case 1 => ENABLE
}

val aggregatedSource = new Trigger().source(inSource, trigger)
```

**Java**

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

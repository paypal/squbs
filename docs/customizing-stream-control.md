# Customizing Stream Control

Pekko Streams/Reactive stream needs to be integrated with the [Runtime Lifecycle](lifecycle.md) of the server. For this, an automated or semi-automated integration is provided through the [`PerpetualStream`](perpetualstream.md) infrastructure. If you need even more fine-grained control over stream, the following sections explain such facilities.

### Dependency

In general, you do not need to add an extra dependency. The classes are part of the following dependency:

```scala
"org.squbs" %% "squbs-unicomplex" % squbsVersion
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
final Source aggregatedSource = new LifecycleManaged().source(inSource);
```

In the Scala API, the resulting source will be an aggregated source materialize to a `(M, () => ActorRef)` where `M` is the materialized type of `inSource` and `() => ActorRef` is the materialized type of the function for accessing the trigger actor which receives events from the Unicomplex, the squbs container.

In the Java API, the resulting source will be an aggregated source materialize to a `org.apache.pekko.japi.Pair<M, Supplier<ActorRef>>` where `M` is the materialized type of `inSource` and `Supplier<ActorRef>` is the materialized type of the function for accessing the trigger actor. Calling the `get()` method on the `Supplier` allows access to the `ActorRef`. This `ActorRef` receives events from the Unicomplex, the squbs container.

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
If you want to respond to more lifecycle events beyond `Active` and `Stopping`, for example you want `Failed` to also stop the flow, you can modify the lifecycle event mapping.

##### Scala

```scala
import org.squbs.stream.TriggerEvent._

val inSource = <your-original-source>
val trigger = LifecycleEventSource()
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
final Source<?, Supplier<ActorRef>> trigger = LifecycleEventSource.create()
    .collect(new PFBuilder<Integer, TriggerEvent>()
        .matchEquals(Active.instance(), p -> ENABLE)
        .matchEquals(Stopping.instance(), p -> DISABLE)
        .matchEquals(Failed.instance(), p -> DISABLE)
        .build()
    );

final Source aggregatedSource = new Trigger(false).source(inSource, trigger);
```

# Timeout Flow

### Overview

Some stream use cases may require each message in a flow to be processed within a bounded time or to return a timeout failure.  Timeout Flow feature adds this feature to standard Akka Stream flows.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

Importing `org.squbs.streams.StreamTimeout._` adds `withTimeout` methods to Akka Stream Flows.  The result is a `Try` of the flow output type:

   * If the message can be processed within the timeout, the output would be a `Success(msg)`.
   * Oherwise, the output would be a `Failure(FlowTimeoutException())`.  

```scala
import org.squbs.streams.StreamTimeout._

val flow = Flow[String].map(s => findTheNumberOfEnglishWordsThatStartWith(s))
Source("a" :: "b" :: "c" :: Nil).
  via(flow.withTimeout(20 milliseconds)).
  runWith(Sink.seq)
```

#### Message ordering

Internally, the timeout flow broadcasts a message to two branches: one is the original flow and the other is a delay stage (for timeout).  Whichever branch processes the message first wins and the slower one needs to be dropped by a deduplication stage. To deduplicate, each message should be uniqely identifiable.  Accordingly, timeout flow generates an id for each message before broadcasting the message and that id is used to deduplicate the messages later in the stream.  

If the original flow cannot guarantee the message ordering (e.g., usage of `mapAsyncUnordered`), to make sure ids are carried along correctly, timeout flow feature requires id to be carried along.  To carry the context, wrap your `Flow` input/output types with `TimerEnvelope[Id, Value]`.  `Id` is the type used to uniquely identify a message, you can set it to `Long` and use the default id generator (or you can also configure your own type and id generator).  Below is an example usage with `TimerEnvelope`.  Since, `mapAsyncUnordered` is used in the stream, `String` is wrapped with `TimerEnvelope[Long, String]`:  

```scala
import org.squbs.streams.StreamTimeout._
import akka.pattern.ask
implicit val askTimeout = Timeout(5.seconds)

val flow = Flow[TimerEnvelope[Long, String]].mapAsyncUnordered(2) { elem =>
  (ref ? elem).mapTo[TimerEnvelope[Long, String]]
}

Source("a" :: "b" :: "c" :: Nil).via(flow.withTimeout(20 milliseconds)).runWith(Sink.seq)
```

#### Configuring `Id` type and `Id` generators

Timeout flow feature defines default `Id` type as `Long` and provides a default `Id` generator.  However, it also allows to configure it.  You can use any type as an `Id` and provide a custom `Id` generator.  `Id` generator is just a function with return type of the `Id`.  Below is an example that uses `BigInt` instead of `Long`:

```scala
class BigIntIdGenerator {
  var id = BigInt(0)
  
  def nextId() = () => {
    id = id + 1
    id
  }
}

import org.squbs.streams.StreamTimeout._
import akka.pattern.ask
implicit val askTimeout = Timeout(5.seconds)

val flow = Flow[TimerEnvelope[BigInt, String]].mapAsyncUnordered(2) { elem =>
  (ref ? elem).mapTo[TimerEnvelope[BigInt, String]]
}

Source("a" :: "b" :: "c" :: Nil).
  via(flow.withTimeout(20 milliseconds, (new BigIntIdGenerator).nextId())).
  runWith(Sink.seq)
```
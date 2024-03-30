# Persistent Buffer

`PersistentBuffer` is the first of a series of practical Pekko Streams flow components. It works like the Pekko Streams buffer with the difference that the content of the buffer is stored in a series of memory-mapped files in the directory given at construction of the `PersistentBuffer`. This allows the buffer size to be virtually limitless, not use the JVM heap for storage, and have extremely good performance in the range of a million messages/second at the same time.

## Dependencies

The following dependencies are required for Persistent Buffer to work:

```scala
"org.squbs" %% "squbs-pattern" % squbsVersion,
"net.openhft" % "chronicle-queue" % "4.16.5"
```

## Examples

The following example shows the use of `PersistentBuffer` in a stream:

```scala
implicit val serializer = QueueSerializer[ByteString]()
val source = Source(1 to 1000000).map { n => ByteString(s"Hello $n") }
val buffer = new PersistentBuffer[ByteString](new File("/tmp/myqueue"))
val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
val countFuture = source.via(buffer.async).runWith(counter)

```

This version shows the same in a GraphDSL:

```scala
implicit val serializer = QueueSerializer[ByteString]()
val source = Source(1 to 1000000).map { n => ByteString(s"Hello $n") }
val buffer = new PersistentBuffer[ByteString](new File("/tmp/myqueue"))
val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
val streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(counter) { implicit builder =>
  sink =>
    import GraphDSL.Implicits._
    source ~> buffer.async ~> sink
    ClosedShape
})
val countFuture = streamGraph.run()
```

## Back-Pressure
`PersistentBuffer` does not back-pressure upstream. It will take all the stream elements given to it and grow its storage by increasing, or rotating, the number of queue files. It does not have any means to determine a limit on the buffer size or determine the storage capacity. Downstream back-pressure is honored as per Pekko Streams and Reactive Streams requirements.

If the `PersistentBuffer` stage gets fused with the downstream, `PersistentBuffer` would not buffer and it would actually back-pressure.  To make sure `PersistentBuffer` actually runs in its own pace, add an `async` boundary just after it. 

## Failure & Recovery

Due to it's persistent nature, `PersistentBuffer` can recover from abrupt stream shutdowns, failures, JVM failures or even potential system failures. A restart of a stream with the `PersistentBuffer` on the same directory will start emitting the elements stored in the buffer and not yet consumed before the newly added elements. Elements consumed from the buffer but not yet finished processing at the time of the previous stream failure or shutdown will cause a loss of only those elements.

Since the buffer is backed by local storage, spindles or SSD, the performance and durability of this buffer is also dependent on the durability of this storage. A system malfunction or storage corruption may cause total loss of all elements in the buffer. So it is important to understand and assume the durability of this buffer not at the level of databases or other off-host persistent stores, in exchange for much higher performance.

Pekko Streams stages batch the requests and buffers the records internally.  `PersistentBuffer` guarantees the recovery and persistence of the records that reached to `onPush`, the records that are in Pekko Stream stage's internal buffer that has not reached to `onPush` yet would be lost during a failure.

## Commit Guarantee

In case of an unexpected failure, elements emitted from the `PersistentBuffer` stage but not yet reached to a `sink` would be lost. Sometimes, it might be required to avoid such data loss. Using a `commit` stage before a `sink` might help in such case.  To add a `commit` stage, use `PersistentBufferAtLeastOnce` instead.  Please see below example for `commit` stage usage:  

```scala
implicit val serializer = QueueSerializer[ByteString]()
val source = Source(1 to 1000000).map { n => ByteString(s"Hello $n") }
val tempPath = new File("/tmp/myqueue")
val config = ConfigFactory.parseMap {
    Map(
      "persist-dir" -> s"${tempPath.getAbsolutePath}"
    )
  }
val buffer = new PersistentBufferAtLeastOnce[ByteString](config)
val commit = buffer.commit[ByteString]
val flowSink = // do some transformation or a sink flow with expected failure
val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
val streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(counter) { implicit builder =>
  sink =>
    import GraphDSL.Implicits._
    // ensures that records are reprocessed when something fails at tranform flow
    source ~> buffer ~> flowSink ~> commit ~> sink 
    ClosedShape
})
val countFuture = streamGraph.run()
```

Please note, `commit` does not prevent the loss of messages in a `sink`'s (or any other stage's after `commit`) internal buffer.

### Commit Order

The `commit` stage should normally receive the elements in the index order.  However, a potential bug in a stream may cause an element to be dropped or reach to `commit` stage out of order.  The default `commit-order-policy` is set to `lenient` to let the stream continue in such scenarios.  You can set it to `strict` for a `CommitOrderException` to be thrown and let the `Supervision.Decider` determine what action to take.

## Space Management

A typical directory for persisting the queue looks like the followings:

```
$ ls -l
-rw-r--r--  1 squbs_user     110054053  83886080 May 17 20:00 20160518.cq4
-rw-r--r--  1 squbs_user     110054053      8192 May 17 20:00 tailer.idx
```

Queue files are deleted automatically once all the readers have successfully processed reading the queue.

## Configuration
The queue can be created by passing just a location of the persistent directory keeping all default configuration. This is seen in all the examples above. Alternatively, it can be created by passing a `Config` object at construction. The `Config` object is a standard [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) configuration. The following example shows constructing a `PersistentBuffer` using a `Config`:

```scala
val configText =
  """
    | persist-dir = /tmp/myQueue
    | roll-cycle = xlarge_daily
    | wire-type = compressed_binary
    | block-size = 80m
  """.stripMargin
val config = ConfigFactory.parseString(configText)

// Construct the buffer using a Config.
val buffer = new PersistentBuffer[ByteString](config)
```

The following configuration properties are used for the `PersistentBuffer`

```sh
persist-dir = /tmp/myQueue # Required
roll-cycle = daily         # Optional, defaults to daily
wire-type = binary         # Optional, defaults to binary
block-size = 80m           # Optional, defaults to 64m
index-spacing = 16k        # Optional, defaults to roll-cycle's spacing 
index-count = 16           # Optional, defaults to roll-cycle's count
commit-order-policy = lenient # Optional, default to lenient
```

Roll-cycle can be specified in lower or upper case. Supported values for `roll-cycle` are as follows:

Roll Cycle  | Capacity
------------|---------
MINUTELY    | 64 million entries per minute
HOURLY      | 256 million entries per hour
SMALL_DAILY | 512 million entries per day
DAILY       | 4 billion entries per day
LARGE_DAILY | 32 billion entries per day
XLARGE_DAILY| 2 trillion entries per day
HUGE_DAILY  | 256 trillion entries per day

Wire-type can be specified in lower or upper case. Supported values for `wire-type` are as follows:

* TEXT
* BINARY
* FIELDLESS_BINARY
* COMPRESSED_BINARY
* JSON
* RAW
* CSV

The memory sizes such as `block-size` and `index-spacing` are specified according to the [memory size format defined in the HOCON specification](https://github.com/typesafehub/config/blob/master/HOCON.md#size-in-bytes-format).

## Serialization
A `QueueSerializer[T]` needs to be implicitly provided for a `PersistentBuffer[T]`, as seen in the examples above:

```scala
implicit val serializer = QueueSerializer[ByteString]()
```

The `QueueSerializer[T]()` call produces a serializer for your target type. It depends on the serialization and deserialization of the underlying infrastructure.

### Implementing a Serializer
To control the fine-grained persistent format in the queue, you may want to implement your own serializer as follows:

```scala
case class Person(name: String, age: Int)

class PersonSerializer extends QueueSerializer[Person] {

  override def readElement(wire: WireIn): Option[Person] = {
    for {
      name <- Option(wire.read().`object`(classOf[String]))
      age <- Option(wire.read().int32)
    } yield { Person(name, age) }
  }

  override def writeElement(element: Person, wire: WireOut): Unit = {
    wire.write().`object`(classOf[String], element.name)
    wire.write().int32(element.age)
  }
}
```

To use this serializer, just declare it implicitly before constructing the `PersistentBuffer` as follows:

```scala
implicit val serializer = new PersonSerializer()
val buffer = new PersistentBuffer[Person](new File("/tmp/myqueue")
```

## Broadcast Buffer
`BroadcastBuffer` is a variant of persistent buffer. This works similar to `PersistentBuffer` except that stream elements are broadcasted to multiple output ports. Hence it is a combination of buffer and broadcast stages. The configuration takes an additional parameter named `output-ports` which specifies the number of output ports.

A broadcast buffer is specially required when stream elements are to be emitted from each output port at an independent rate depending on the speed of downstream demand.

```scala
val configText =
  """
    | persist-dir = /tmp/myQueue
    | roll-cycle = xlarge_daily
    | wire-type = compressed_binary
    | block-size = 80m
    | output-ports = 3
  """.stripMargin
val config = ConfigFactory.parseString(configText)

// Construct the buffer using a Config.
val bcBuffer = new BroadcastBuffer[ByteString](config)
``` 

## Examples

```scala
implicit val serializer = QueueSerializer[ByteString]()

val in = Source(1 to 100000)
val flowCounter = Flow[Any].map(_ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)

val streamGraph = RunnableGraph.fromGraph(GraphDSL.createGraph(flowCounter) { implicit builder =>
      sink =>
        import GraphDSL.Implicits._
        val buffer = new BroadcastBufferAtLeastOnce[ByteString](config)
        val commit = buffer.commit[ByteString]
        val bcBuffer = builder.add(buffer.async)
        val mr = builder.add(merge)
        in ~> transform ~> bcBuffer ~> commit ~> mr ~> sink
                           bcBuffer ~> commit ~> mr
                           bcBuffer ~> commit ~> mr
        ClosedShape
    })
    
val countFuture = streamGraph.run()
```
## Credits

`PersistentBuffer` utilizes [Chronicle-Queue](https://github.com/OpenHFT/Chronicle-Queue) 4.x as high-performance memory-mapped queue persistence.

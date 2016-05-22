#Persistent Buffer

`PersistentBuffer` is the first of a series of practical Akka Streams flow components. It works like the Akka Streams buffer with the difference that the content of the buffer is stored in a series of memory-mapped files in the directory given at construction of the `PersistentBuffer`. This allows the buffer size to be virtually limitless, not use the JVM heap for storage, and have extremely good performance in the range of a million messages/second at the same time.

##Examples

The following example shows the use of `PersistentBuffer` in a stream:

```scala
val source = Source(1 to 1000000).map { n => ByteString(s"Hello $n") }
val buffer = new PersistentBuffer(new File("/tmp/myqueue"))
val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
val countFuture = source.via(buffer).runWith(counter)

```

This next version shows the same in a GraphDSL:

```scala
val source = Source(1 to 1000000).map { n => ByteString(s"Hello $n") }
val buffer = new PersistentBuffer(new File("/tmp/myqueue")
val counter = Flow[Any].map( _ => 1L).reduce(_ + _).toMat(Sink.head)(Keep.right)
val streamGraph = RunnableGraph.fromGraph(GraphDSL.create(counter) { implicit builder =>
  sink =>
    import GraphDSL.Implicits._
    source ~> buffer ~> sink
    ClosedShape
})
val countFuture = streamGraph.run()
```

##Data Type

For optimal performance, data type supported by `PersistentBuffer` is currently limited to `akka.util.ByteString`. Other data types will need to be serialized/converted to `ByteString` before flowing into the buffer, and deserialized/converted from `ByteString` flowing out of the buffer. This has to be handled as part of the stream.

##BackPressure
`PersistentBuffer` does not backpressure upstream. It will take all the stream elements given to it and grow its storage by increasing the number of queue files. It does not have any means to place a limit on the buffer size or determine the storage capacity. Please see **Cleaning Up** below for strategies to manage storage. Downstream backpressure is honored as per Akka Streams and Reactive Streams requirements.

##Failure & Recovery

Due to it's persistent nature, `PersistentBuffer` can recover from abrupt stream shutdowns, failures, JVM failures or even potential system failures. A restart of a stream with the `PersistentBuffer` on the same directory will start emitting the elements stored in the buffer and not yet consumed before the newly added elements. Elements consumed from the buffer but not yet finished processing at the time of the previous stream failure or shutdown will cause a loss of only those elements.

Since the buffer is backed by local storage, spindles or SSD, the performance and durability of this buffer is also dependent on the durability of this storage. A system malfunction or storage corruption may cause total loss of all elements in the buffer. So it is important to understand and assume the durability of this buffer not at the level of databases or other off-host persistent stores, in exchange for much higher performance.

##Space Management

A typical directory for persisting the queue looks like the followings:

```
$ ls -l
-rw-r--r--  1 squbs_user     110054053  83886080 May 17 20:00 20160518.cq4
-rw-r--r--  1 squbs_user     110054053      8192 May 17 20:00 tailer.idx
```

Up to 32 of these `*.cq4` files will be created over time. After 32, old files will be recycled and re-used for new data. There is no need to remove these files as long as there is adequate space for all 32 files of static size.

##Credits

`PersistentBuffer` utilizes [Chronicle-Queue](https://github.com/OpenHFT/Chronicle-Queue) 4.x as high-performance memory-mapped queue persistence.
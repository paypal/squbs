# Materialization Metrics Collector Stage

### Overview

`MaterializationMetricsCollector` is an Pekko Streams `GraphStage` to collect materialization metrics for a stream:

   * active materialization counts
   * new materialization creation rates
   * materialization termination rates (aggregation of successful termination and failures)


A prominent use case for the `MaterializationMetricsCollector` is with server-side Pekko HTTP, where each new connection results in a stream materialization and therefore each connection termination results in the termination of the associated stream materialization. For this reason, the `MaterializationMetricsCollector` is out-of-the-box integrated with [squbs HTTP(S) service](http-services.md) implementations publishing active connection, connection creation, and connection termination metrics.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The usage is very similar to standard Pekko Stream stages.  In below examples, you should see JMX beans with names that contain:

   * `my-stream-active-count` with `Count` value 2 at the beginning, but will go down to 0 once the materializations terminate.
   * `my-stream-creation-count` with `Count` value 2.
   * `my-stream-termination-count` will shows up only after a stream terminates and will have `Count` value 2 ultimately.


##### Scala

```scala
val stream = Source(1 to 10)
  .via(MaterializationMetricsCollector[Int]("my-stream"))
  .to(Sink.ignore)

stream.run()
stream.run() // Materializing the stream a second time, thus increasing the Count value to 2
```      

##### Java

```java
RunnableGraph<NotUsed> stream =
        Source.range(1, 10)
                .via(MaterializationMetricsCollector.create("my-stream", system))
                .to(Sink.ignore());

stream.run(mat);
stream.run(mat); // Materializing the stream a second time, thus increasing the Count value to 2
```   

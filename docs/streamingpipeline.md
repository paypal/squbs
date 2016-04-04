#Streaming Request/Response Pipeline

### Overview

We often need to have common infrastructure functionality across different squbs services, or as organizational standards.  Such infrastructure includes, but is not limited to logging, request tracing, authentication/authorization, tracking, cookie management, A/B testing, etc.

As squbs promotes separation of concerns, such logic would belong to infrastructure and not service implementation.  The squbs streaming pipeline allows infrastructure to provide components installed into a service without service owner having to worry about such aspects in their service or actors.

Generally speaking, a squbs streaming pipeline is a Bidi Flow acting as a bridge in between the Akka Http layer and the squbs service.  That is to say:

   * All request messages sent from Akka Http to squbs service will go thru the pipeline
   * Vice versa, all response messages sent from squbs service will go thru the pipeline.

### Streaming pipeline declaration

In `squbs-meta.conf`, you can specify the pipeline for your service:

```
squbs-services = [
  {
    class-name = org.squbs.sample.MyActor
    web-context = mypath
    pipeline = dummyflow
  }
]
```
* If there are no custom pipeline for a squbs-service, just omit.
* Default pre/post flows specified via the below configuration are automatically connected to the pipeline unless `defaultPipelineOn` is set to `false`:

```
squbs.pipeline.streaming.defaults {
	pre-flow = defaultPreFlow
	post-flow = defaultPostFlow
}
```

With the above configuration, the pipeline would look like:

```
                 +---------+   +---------+   +---------+   +---------+
RequestContext ~>|         |~> |         |~> |         |~> |         | 
                 | default |   |  dummy  |   | default |   |  squbs  |
                 | PreFlow |   |  flow   |   | PostFlow|   | service | 
RequestContext <~|         |<~ |         |<~ |         |<~ |         |
                 +---------+   +---------+   +---------+   +---------+
```

`RequestContext` is basically a wrapper around `HttpRequest` and `HttpResponse`, which also allows carrying context information.

### Bidi Flow Configuration

A bidi flow can be specified as below:

```
dummyflow {
  type = squbs.pipelineflow
  factory = org.squbs.sample.DummyBidiFlow
}
```

* type: to idenfity the configuration as a `squbs.pipelineflow`.
* factory: the factor class to create the `BidiFlow` from.

A sample `DummyBidiFlow` looks like below:

```scala
class DummyBidiFlow extends PipelineFlowFactory {

  override def create: PipelineFlow = {
     BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      val inbound = b.add(Flow[RequestContext].map { rc => rc.addRequestHeader(RawHeader("DummyRequest", "ReqValue")) })
      val outbound = b.add(Flow[RequestContext].map{ rc => rc.addResponseHeader(RawHeader("DummyResponse", "ResValue"))})
      BidiShape.fromFlows(inbound, outbound)
    })
  }
}
```

#### Aborting the flow
In certain scenarios, a stage in pipeline may have a need to abort the flow immediately and return an `HttpResponse`, e.g., in case of authentication/authorization.  In such scenarios, the rest of the pipeline should be skipped and the request should not reach to the squbs service.  To skip the rest of the flow: 

* the flow needs to be added to builder with `abortable`, e.g., `b.add(authorization abortable)`.
* set the `HttpResponse` on `RequestContext` when you need to abort.

In the below `DummyAbortableBidiFlow ` example, `authorization ` is a bidi flow with `abortable` and it aborts the flow is user is not authorized: 

```scala
class DummyAbortableBidiFlow extends PipelineFlowFactory {

  override def create: PipelineFlow = {

    BidiFlow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val inboundA = b.add(Flow[RequestContext].map { rc => rc.addRequestHeader(RawHeader("keyInA", "valInA")) })
      val inboundC = b.add(Flow[RequestContext].map { rc => rc.addRequestHeader(RawHeader("keyInC", "valInC")) })
      val outboundA = b.add(Flow[RequestContext].map { rc => rc.addResponseHeaders(RawHeader("keyOutA", "valOutA"))})
      val outboundC = b.add(Flow[RequestContext].map { rc => rc.addResponseHeaders(RawHeader("keyOutC", "valOutC"))})

      val inboundOutboundB = b.add(authorization abortable)

      inboundA ~>  inboundOutboundB.in1
                   inboundOutboundB.out1 ~> inboundC
                   inboundOutboundB.in2  <~ outboundC
      outboundA <~ inboundOutboundB.out2

      BidiShape(inboundA.in, inboundC.out, outboundC.in, outboundA.out)
    })
  }

  val authorization = BidiFlow.fromGraph(GraphDSL.create() { implicit b =>

    val authorization = b.add(Flow[RequestContext] map { rc =>
        if(!isAuthorized) {
          rc.copy(response = Some(HttpResponse(StatusCodes.Unauthorized, entity = "~> ~> bypassing in inbound")))
        } else rc
    })

    val noneFlow = b.add(Flow[RequestContext]) // Do nothing

    BidiShape.fromFlows(authorization, noneFlow)
  })
}
```

Once a flow is added with `abortable`, a bidi flow gets connected.  This bidi flow checks the existence of `HttpResponse` and bypasses or sends the request downstream.  Here is how the above `DummyAbortableBidiFlow` looks:


```
                                                +-----------------------------------+
                                                |  +-----------+    +-----------+   |   +-----------+
                  +-----------+   +---------+   |  |           | ~> |  filter   o~~~0 ~>|           |
                  |           |   |         |   |  |           |    |not aborted|   |   | inboundC  | ~> RequestContext
RequestContext ~> | inboundA  |~> |         |~> 0~~o broadcast |    +-----------+   |   |           |
                  |           |   |         |   |  |           |                    |   +-----------+
                  +-----------+   |         |   |  |           | ~> +-----------+   |
                                  | inbound |   |  +-----------+    |  filter   |   |
                                  | outbound|   |                   |  aborted  |   |
                  +-----------+   |   B     |   |  +-----------+ <~ +-----------+   |   +-----------+
                  |           |   |         |   |  |           |                    |   |           |
RequestContext <~ | outboundA | <~|         | <~0~~o   merge   |                    |   | outboundC | <~ RequestContext
                  |           |   |         |   |  |           o~~~~~~~~~~~~~~~~~~~~0 <~|           |
                  +-----------+   +---------+   |  +-----------+                    |   +-----------+
                                                +-----------------------------------+

```
#Handling Http Requests with a FlowDefinition

Akka Http defines the low level request handling using `Flow[HttpRequest, HttpResponse, NotUsed]`. This document shows how such a flow can be registered against squbs through a `FlowDefinition`.

##Defining the Flow

To define the flow to be integrated into the request flow in squbs, you want to define a class with a public, no-argument constructor extending the `FlowDefinition` trait as follows:

```scala
class SampleFlowSvc extends FlowDefinition {

  def flow = Flow[HttpRequest].map {
  
    // A simple ping/pong request/response.
    case HttpRequest(_, Uri(_, _, Path("ping"), _, _), _, _, _) =>
      HttpResponse(StatusCodes.OK, entity = "pong")

    // This example does both request and response chunking.
    case req @ HttpRequest(_, Uri(_, _, Path("chunked"), _, _), _, _, _) =>

      val responseChunks = req.entity.dataBytes.filter(_.nonEmpty)
        .map { b => (1, b.length) }
        .reduce { (a, b) => (a._1 + b._1, a._2 + b._2) }
        .flatMapConcat { case (chunkCount, byteCount) =>
          val response = s"Received $chunkCount chunks and $byteCount bytes.\r\n"
          Source(response.split(' ').toList).map { ChunkStreamPart(_) }
        }

      HttpResponse(StatusCodes.OK, entity = Chunked(ContentTypes.`text/plain(UTF-8)`, responseChunks))
}
```

Of course, you're still encouraged to use the higher level `RouteDefinition`. But using low level artifacts has a few benefits in terms of memory consumption.

##Rules and Behavior of the FlowDefinition

There are a few rules you have to keep in mind when implementing a `FlowDefinition`:

1. **Exactly one response:** It is the responsibility of the application to generate exactly one response for every request.
2. **Response ordering:** The ordering of responses matches the ordering of the associated requests (which is relevant if HTTP pipelining is enabled where processing of multiple incoming requests may overlap).
3. **Concurrent state access:** A single `FlowDefinition`'s flow can be materialized multiple times, causing multiple instances of the `Flow` itself. If they access any state in the `FlowDefinition`, it is important to note such access can be concurrent, both for reads and writes. It is not safe for such accesses to read or write mutable state inside the `FlowDefinition`. The use of Akka `Actor`s or `Agent`s is highly encouraged in such situations.
4. **Access to actor context:** The `FlowDefinition` has access to the `ActorContext` with the field `context` by default. This can be used to create new actors or access other actors.
5. **Access to web context:** If the `WebContext` trait is mixed in, the `FlowDefinition` will have access to the field `webContext`. This field is used to determine the web context or path from the root where this `FlowDefinition` is handling requests.
6. **Request path:** The `HttpRequest` object is handed to this flow unmodified. The `webContext` is in the `Path` of the request. It is the `FlowDefinition`'s job (as seen above) to handle the request with the knowledge of the `webContext`.

##FlowDefinition Registration

A FlowDefinition should be registered in `squbs-meta.conf` as below:

```
cube-name = org.mycube.SomeFlowSvc
cube-version = "0.0.1"
squbs-services = [
    {
        class-name = org.mycube.SampleFlowSvc
        web-context = sampleflow
    }
]
```
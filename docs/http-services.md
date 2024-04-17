# Implementing HTTP(S) Services

[TOC]

## Overview

HTTP is the most pervasive integration protocol. It is the basis for web services, both client and server side. pekko HTTP provides great server and client side APIs. squbs has the intention to maintain these APIs without change. Instead, squbs provides the infrastructure allowing production-ready use of these APIs by providing standard configuration for HTTP listeners that services can use to accept and handle requests, and pipelines allowing logging, monitoring, authentication/authorization, before the request comes to the application and after the response leaves the application before it goes onto the wire.

squbs supports pekko HTTP, both the low-level and high-level server-side APIs, for defining services. Both APIs enjoy the full productionalization support such as listeners, pipelines, logging, and monitoring. In addition, squbs supports both Scala and Java flavors of service definitions. These service handlers are declared in classes and registered to squbs in the metadata through the `META-INF/squbs-meta.conf` file through the `squbs-services` entry in this file. Each style of service is registered in the same manner, just by providing the class name and configuration.

All squbs service definitions have access to the field `context` which is an `pekko.actor.ActorContext` useful for accessing the actor system, scheduler, and a variety of pekko facilities.

## Dependencies

The following dependency is needed for starting the server as well as registering service definitions:

```
"org.squbs" %% "squbs-unicomplex" % squbsVersion
```


## Defining the Service

Services can be defined in either Scala or Java, using either the high-level or low-level API. Service definition classes **MUST HAVE no-argument constructors** and must be registered in order to handle incoming HTTP requests.

### High-Level Scala API

The high-level server-side API is represented by pekko HTTP's `Route` artifact and its directives. To use a `Route` to handle requests, just provide a class extending the `org.squbs.unicomplex.RouteDefinition` trait and provide the `route` function as follows:

```scala
import org.apache.pekko.http.scaladsl.server.Route
import org.squbs.unicomplex.RouteDefinition

class PingPongSvc extends RouteDefinition {

  override def route: Route = path("ping") {
    get {
      complete("pong")
    }
  }
  
  // Overriding the rejectionHandler is optional
  override def rejectionHandler: Option[RejectionHandler] =
    Some(RejectionHandler.newBuilder().handle {
      case ServiceRejection => complete("rejected")
    }.result())

  // Overriding the exceptionHandler is optional
  override def exceptionHandler: Option[ExceptionHandler] =
    Some(ExceptionHandler {
      case _: ServiceException => complete("exception")
    })
}
```

In addition to defining the `route`, you can also provide a [`RejectionHandler`](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/rejections.html#the-rejectionhandler) and an [`ExceptionHandler`](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/exception-handling.html#exception-handling) by overriding the `rejectionHandler` and `exceptionHandler` functions accordingly. These can be seen in the example above.

Please refer to the [pekko HTTP high-level API](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/index.html), [Routing DSL](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/overview.html), [Directives](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/directives/index.html), [Rejection](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/rejections.html), and [Exception Handling](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/exception-handling.html) documentation to fully utilize these APIs.

### Low-Level Scala API

Using the Scala low-level API, just extend `org.squbs.unicomplex.FlowDefinition` and override the `flow` function. The `flow` needs to be of type `Flow[HttpRequest, HttpResponse, NotUsed]` using the Scala DSL and model provided by pekko HTTP as follows:

```scala
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.scaladsl.Flow
import org.squbs.unicomplex.FlowDefinition

class SampleFlowSvc extends FlowDefinition {

  override def flow = Flow[HttpRequest].map {
    case HttpRequest(_, Uri(_, _, Path("ping"), _, _), _, _, _) =>
      HttpResponse(StatusCodes.OK, entity = "pong")
    case _ =>
      HttpResponse(StatusCodes.NotFound, entity = "Path not found!")
}
```

This provides access to the `Flow` representation of the pekko HTTP low-level server-side API. Please refer to the [pekko HTTP low-level API](http://doc.pekko.io/docs/pekko-http/current/scala/http/low-level-server-side-api.html#streams-and-http), [pekko Streams](http://doc.pekko.io/docs/pekko/current/scala/stream/stream-quickstart.html), and the [HTTP Model](http://doc.pekko.io/docs/pekko-http/current/scala/http/common/http-model.html#http-model-scala) documentation for further information on constructing more sophisticated `Flow`s.

### High-Level Java API

The high-level server-side API is represented by pekko HTTP's `Route` artifact and its directives. To use a `Route` to handle requests, just provide a class extending the `org.squbs.unicomplex.RouteDefinition` trait and provide the `route` method as follows:

```java
import org.apache.pekko.http.javadsl.server.ExceptionHandler;
import org.apache.pekko.http.javadsl.server.RejectionHandler;
import org.apache.pekko.http.javadsl.server.Route;
import org.squbs.unicomplex.AbstractRouteDefinition;

import java.util.Optional;

public class JavaRouteSvc extends AbstractRouteDefinition {

    @Override
    public Route route() {
        return route(
                path("ping", () ->
                        complete("pong")
                ),
                path("hello", () ->
                        complete("hi")
                ));
    }

	// Overriding the rejection handler is optional
    @Override
    public Optional<RejectionHandler> rejectionHandler() {
        return Optional.of(RejectionHandler.newBuilder()
                .handle(ServiceRejection.class, sr ->
                        complete("rejected"))
                .build());
    }

    // Overriding the exception handler is optional
    @Override
    public Optional<ExceptionHandler> exceptionHandler() {
        return Optional.of(ExceptionHandler.newBuilder()
                .match(ServiceException.class, se ->
                        complete("exception"))
                .build());
    }
}
```

In addition to defining the `route`, you can also provide a [`RejectionHandler`](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/rejections.html#the-rejectionhandler) and an [`ExceptionHandler`](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/exception-handling.html#exception-handling) by overriding the `rejectionHandler` and `exceptionHandler` methods accordingly. These can be seen in the example above.

Please refer to the [pekko HTTP high-level API](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/index.html), [Routing DSL](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/overview.html), [Directives](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/directives/index.html), [Rejection](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/rejections.html), and [Exception Handling](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/exception-handling.html) documentation to fully utilize these APIs.

### Low-Level Java API

To use the Java low-level API, just extend `org.squbs.unicomplex.AbstractFlowDefinition` and override the `flow` method. The `flow` needs to be of type `Flow[HttpRequest, HttpResponse, NotUsed]` using the Java DSL and model provided by pekko HTTP. Note the imports in the following:

```java
import org.apache.pekko.NotUsed;
import org.apache.pekko.http.javadsl.model.*;
import org.apache.pekko.stream.javadsl.Flow;
import org.squbs.unicomplex.AbstractFlowDefinition;

public class JavaFlowSvc extends AbstractFlowDefinition {

    @Override
    public Flow<HttpRequest, HttpResponse, NotUsed> flow() {
        return Flow.of(HttpRequest.class)
                .map(req -> {
                    String path = req.getUri().path();
                    if (path.equals(webContext() + "/ping")) {
                        return HttpResponse.create().withStatus(StatusCodes.OK).withEntity("pong");
                    } else {
                        return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND).withEntity("Path not found!");
                    }
                });
    }
}
```

**Note:** The `webContext()` method as well as the `context()` method for accessing the actor context are provided by the `AbstractFlowDefinition` class.

This provides access to the `Flow` representation of the pekko HTTP low-level server-side API. Please refer to the [pekko HTTP low-level API](http://doc.pekko.io/docs/pekko-http/current/java/http/server-side/low-level-server-side-api.html#streams-and-http), [pekko Streams](http://doc.pekko.io/docs/pekko/current/java/stream/stream-quickstart.html), and the [HTTP model](http://doc.pekko.io/docs/pekko-http/current/java/http/http-model.html#http-model-java) documentation for further information on constructing more sophisticated `Flow`s.

## Service Registration

Service metadata is declared in `META-INF/squbs-meta.conf` as shown in the following example.

```
cube-name = org.sample.sampleflowsvc
cube-version = "0.0.2"
squbs-services = [
  {
    class-name = org.sample.SampleFlowSvc
    web-context = sample # You can also specify bottles/v1, for instance.
    
    # The listeners entry is optional, and defaults to 'default-listener'.
    listeners = [ default-listener, my-listener ]
    
    # Optional, defaults to a default pipeline.
    pipeline = some-pipeline
    
    # Optional, disables the default pipeline if set to false.  If missing, it is set to on.
    defaultPipeline = on
    
    # Optional, only applies to actors.
    init-required = false
  }
]
```

The class-name parameter identifies the service definition class which could use either the low-level or high-level API and could be implemented in either Java or Scala.

The web-context is a string that uniquely identifies the web context of a request to be dispatched to this service. Please refer to [The Web Context](#the-web-context) below for detailed discussions on the web context.

Optionally, the listeners parameter declares a list of listeners to bind this service. Listener binding is discussed in the [Listener Binding](#listener-binding) section, below.

The pipeline is a set of request pre- and post-processors before and after the request gets processed by the request handler. The pipeline name can be specified by a `pipeline` parameter. Along with the pipeline specified, a default pipeline set in the configuration will be plugged together for the request/response. To disable the default pipeline for this service, you can set `defaultPipeline = off` in `META-INF/squbs-meta.conf`. Please refer to [Request/Response Pipeline](pipeline.md) for more information.

### Listener Binding

Unlike programming to pekko HTTP directly, squbs provides all socket binding and connection management through its listeners. Just provide the request/response handling through one or more of the APIs discussed above and register those implementations to squbs. This allows standardization of the binding configuration across services and allows uniform configuration management across services.

A listener is declared in `application.conf` or `reference.conf`, usually living in the project's `src/main/resources` directory. Listeners declare interfaces, ports, HTTPS security attributes, and name aliases, and are explained in [Configuration](configuration.md#listeners).

A service handler attaches itself to one or more listeners. The `listeners` attribute is a list of listeners or aliases the handler should bind to. If listeners are not defined, it will default to the `default-listener`.

The wildcard value `"*"` (note, it has to be quoted or will not be properly be interpreted) is a special case which translates to attaching this handler to all active listeners. By itself, it will however not activate any listener if it is not already activated by a concrete attachment of a handler. If the handler should activate the default listener and attach to any other listener activated by other handlers, the concrete attachment has to be specified separately as follows:

```
listeners = [ default-listener, "*" ]
```

## The Web Context

Each service entry point is bound to a unique web context which is the leading path segments separated by the `/` character. For instance, the url `http://mysite.com/my-context/index` would match the context `"my-context"`, if registered. It can also match the root context if `"my-context"` is not registered. Web contexts are not necessarily the first slash-separated segment of the path. Dependent on the context registration, it may match multiple such segments. A concrete example would be a URL with service versioning. The URL `http://mysite.com/my-context/v2/index` may have either `my-context` or `my-context/v2` as the web context, depending on what contexts are registered. If both `my-context` and `my-context/v2` are registered, the longest match - in this case `my-context/v2` will be used for routing the request. This is useful for separating different versions of the web interface or API into different cubes/modules.

The registered web context **MUST NOT** start with a `/` character. It can have `/` characters inside as segment separators in case of multi-segment contexts. And it is allowed to be `""` for root context. If multiple services match the request, the longest context match takes precedence.

While the web context is registered in metadata, the `route`, and especially the `flow` defined in the low level API needs to know what web context it is serving.

* Java service handler classses have direct access to the `webContext()` method.
* Scala service handler classes will want to mix in the `org.squbs.unicomplex.WebContext` trait. Doing so will add the following field to your class:

   ```scala
   val webContext: String
   ```

The webContext field is initialized to the value of the registered web context as set in metadata upon construction of the object as shown below:

```scala
class SampleFlowSvc extends FlowDefinition with WebContext {

  def flow = Flow[HttpRequest].map {
    case HttpRequest(_, Uri(_, _, Path(s"$webContext/ping"), _, _), _, _, _) =>
      HttpResponse(StatusCodes.OK, entity = "pong")
    case _ =>
      HttpResponse(StatusCodes.NotFound, entity = "Path not found!")
  }
}
```

## Rules and Behaviors the High-Level Route API

1. **Concurrent state access:** The provided `route` can be used by multiple connections, and therefore threads, concurrently. If the `route` accesses any state in the encapsulating `RouteDefinition` (Scala) or `AbstractRouteDefinition` (Java) class, it is important to note such access can be concurrent, both for reads and writes. It is not safe for such accesses to read or write mutable state inside the encapsulating class. The use of pekko `Actor`s is highly encouraged in such situations.
2. **Access to actor context:** The `RouteDefinition`/`AbstractRouteDefinition` has access to the `ActorContext` with the `context` field (Scala) or `context()` method (Java) by default. This can be used to create new actors or access other actors.
3. **Access to web context:** For the Scala `RouteDefinition`, if the `WebContext` trait is mixed in, it will have access to the field `webContext`. The Java `AbstractRouteDefinition` provides the `webContext()` method in all cases. This field/method is used to determine the web context or path from the root where this `RouteDefinition`/`AbstractRouteDefinition` is handling requests. 

## Rules and Behaviors of the Low-Level Flow API

There are a few rules you have to keep in mind when implementing a `FlowDefinition` (Scala) or `AbstractFlowDefinition` (Java):

1. **Exactly one response:** It is the responsibility of the application to generate exactly one response for every request.
2. **Response ordering:** The ordering of responses matches the ordering of the associated requests, which is relevant if HTTP pipelining is enabled where processing of multiple incoming requests may overlap.
3. **Concurrent state access:** The flow can be materialized multiple times, causing multiple instances of the `Flow` itself. If these instances access any state in the encapsulating `FlowDefinition` or `AbstractFlowDefinition`, it is important to note such access can be concurrent, both for reads and writes. It is not safe for such accesses to read or write mutable state inside the encapsulating class. The use of pekko `Actor`s is highly encouraged in such situations.
4. **Access to actor context:** The `FlowDefinition`/`AbstractFlowDefinition` has access to the `ActorContext` with the `context` field (Scala) or `context()` method (Java) by default. This can be used to create new actors or access other actors.
5. **Access to web context:** For the Scala `FlowDefinition`, if the `WebContext` trait is mixed in, it will have access to the field `webContext`. The Java `AbstractFlowDefinition` provides the `webContext()` method in all cases. This field/method is used to determine the web context or path from the root where this `FlowDefinition`/`AbstractFlowDefinition` is handling requests. 
6. **Request path:** The `HttpRequest` object is handed to this flow unmodified. The `webContext` is in the `Path` of the request. It is the users job (as seen above) to handle the request with the knowledge of the `webContext`. In other words, the low-level API handles the `HttpRequest` directly and needs to manually take the web context into consideration for any path matching.

## Metrics

squbs comes with pre-built [pipeline](#pipeline) elements for metrics collection and squbs giter8 templates sets those as default.  Accordingly, each squbs http(s) service is enabled to collect [Codahale Metrics](http://metrics.dropwizard.io/3.1.0/getting-started/) out-of-the-box without any code change or configuration.  Please note, squbs metrics collection does NOT require AspectJ or any other runtime code weaving.  The following metrics are available on JMX by default:

   * Request Level Metrics:
      * Request Timer
      * Request Count Meter
      * A meter for each http response status code category: 2xx, 3xx, 4xx, 5xx
      * A meter for each exception type that was returned by the service.
  * Connection Level Metrics:
     * Active Connections Counter
     * Connection Creation Meter
     * Connection Termination Meter


You can access the `MetricRegistry` by `MetricsExtension(system).metrics`.  This allows you to create further meters, timers, histograms, etc or to pass it to a different type metrics reporter.

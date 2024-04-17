# pekko HTTP Client on Steroids

### Overview

`squbs-httpclient` project adds operationalization aspects to [pekko HTTP Host-Level Client-Side API](http://doc.pekko.io/docs/pekko-http/current/scala/http/client-side/host-level.html) while keeping the pekko HTTP API.  Here is the list of features it adds:

* [Service Discovery](#service-discovery-chain): Lets any service discovery mechanism to be plugged in and allows resolving HTTP endpoints by string identifiers, e.g., `paymentserv`.
* [Per Client configuration](#per-client-configuration): Let's each client to individually override defaults in `application.conf`.
* [Pipeline](#pipeline): Allows a `Bidi`pekko Streams flow to be registered globally or individually for clients.
* [Metrics](#metrics): Provides [Codahale Metrics](http://metrics.dropwizard.io/3.1.0/getting-started/) out-of-the-box for each client **without** AspectJ.
* [JMX Beans](#jmx-beans): Exposes the configuration of each client as JMX beans.
* [Circuit Breaker](#circuit-breaker): Provides resiliency with a stream based circuit breaker.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-httpclient" % squbsVersion
```

### Usage

`squbs-httpclient` project sticks to the pekko HTTP API.  The only exception is during the creation of host connection pool.  Instead of `Http().cachedHostConnectionPool`, it defines `ClientFlow` with the same set of parameters (and few extra optional parameters).

##### Scala

Similar to the example at [pekko HTTP Host-Level Client-Side API](http://doc.pekko.io/docs/pekko-http/current/scala/http/client-side/host-level.html#example), the Scala use of `ClientFlow` is as follows:      
  

```scala
// construct a pool client flow with context type `Int`
val poolClientFlow = ClientFlow[Int]("sample") // Only this line is specific to squbs.  Takes implicit ActorSystem.

val responseFuture: Future[(Try[HttpResponse], Int)] =
  Source.single(HttpRequest(uri = "/") -> 42)
    .via(poolClientFlow)
    .runWith(Sink.head)
```

You can pass optional settings, e.g., `HttpsConnectionContext` and `ConnectionPoolSettings`, to `ClientFlow`.

```scala
val clientFlow = ClientFlow("sample", Some(connectionContext), Some(connectionPoolSettings))
```

##### Java

Also, similar to the example at [pekko HTTP Host-Level Client-Side API](http://doc.pekko.io/docs/pekko-http/current/java/http/client-side/host-level.html#example), the Java use of `ClientFlow` is as follows:

```java
final ActorSystem system = ActorSystem.create();
final Materializer mat = Materializer.createMaterializer(system);

final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool>
    clientFlow = ClientFlow.create("sample", system, mat); // Only this line is specific to squbs
    
final CompletionStage<Pair<Try<HttpResponse>, Integer>> responseFuture =
    Source.single(Pair.create(HttpRequest.create().withUri("/"), 42))
        .via(clientFlow)
        .runWith(Sink.head(), mat);
```

You can pass `Optional` settings, e.g., `HttpsConnectionContext` and `ConnectionPoolSettings`, to `ClientFlow.create`.  It also provides a fluent API:

```java
final Flow<Pair<HttpRequest, Integer>, Pair<Try<HttpResponse>, Integer>, HostConnectionPool> clientFlow =
        ClientFlow.<Integer>builder()
                .withSettings(connectionPoolSettings)
                .withConnectionContext(connectionContext)
                .create("sample", system, mat);
```

#### HTTP Model

##### Scala

Below is an `HttpRequest` creation example in Scala.  Please see [HTTP Model Scala documentation](http://doc.pekko.io/docs/pekko-http/current/scala/http/common/http-model.html) for more details:

```scala
import HttpProtocols._
import MediaTypes._
  
val charset = HttpCharsets.`UTF-8`
val userData = ByteString("abc", charset.nioCharset())
val authorization = headers.Authorization(BasicHttpCredentials("user", "pass"))

HttpRequest(
  PUT,
  uri = "/user",
  entity = HttpEntity(`text/plain` withCharset charset, userData),
  headers = List(authorization),
  protocol = `HTTP/1.0`)
```
##### Java

Below is an `HttpRequest` creation example in Java.  Please see [Http Model Java documentation](http://doc.pekko.io/docs/pekko-http/current/java/http/http-model.html) for more details:

```java
import HttpProtocols.*;
import MediaTypes.*;

Authorization authorization = Authorization.basic("user", "pass");
HttpRequest complexRequest =
    HttpRequest.PUT("/user")
        .withEntity(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, "abc"))
        .addHeader(authorization)
        .withProtocol(HttpProtocols.HTTP_1_0);
```

### Service Discovery Chain

`squbs-httpclient` does not require a hostname/port combination to be provided during client pool creation.  Instead it allows a service discovery chain to be registered which allows resolving `HttpEndpoint`s by a string identifier by running through the registered service discovery mechanisms.  For instance, in the above example, `"sample"` is the logical name of the service that client wants to connect, the configured service discovery chain will resolve it to an `HttpEndpoint` which includes host and port, e.g., `http://pekko.io:80`.

Please note, you can still pass a valid http URI as a string to `ClientFlow` as a default resolver to resolve valid http URIs is pre-registered in the service discovery chain by default:

   * `ClientFlow[Int]("http://pekko.io")` in Scala
   * `ClientFlow.create("http://pekko.io", system, mat)` in Java

There are two variations of registering resolvers as shown below. The closure style allows more compact and readable code. However, the subclass has the power to keep state and make resolution decisions based on such state:

##### Scala

Register function type `(String, Env) => Option[HttpEndpoint]`:

```scala
ResolverRegistry(system).register[HttpEndpoint]("SampleEndpointResolver") { (svcName, env) =>
  svcName match {
    case "sample" => Some(HttpEndpoint("http://pekko.io:80"))
    case "google" => Some(HttpEndpoint("http://www.google.com:80"))
    case _ => None
  }
}
```

Register class extending `Resolver[HttpEndpoint]`:

```scala
class SampleEndpointResolver extends Resolver[HttpEndpoint] {
  override def name: String = "SampleEndpointResolver"

  override def resolve(svcName: String, env: Environment): Option[HttpEndpoint] =
    svcName match {
      case "sample" => Some(Endpoint("http://pekko.io:80"))
      case "google" => Some(Endpoint("http://www.google.com:80"))
      case _ => None
    }
}

// Register EndpointResolver
ResolverRegistry(system).register[HttpEndpoint](new SampleEndpointResolver)
```

##### Java

Register `BiFunction<String, Environment, Optional<HttpEndpoint>>`:

```java
ResolverRegistry.get(system).register(HttpEndpoint.class, "SampleEndpointResolver", (svcName, env) -> {
    if ("sample".equals(svcName)) {
        return Optional.of(HttpEndpoint.create("http://pekko.io:80"));
    } else if ("google".equals(svcName))
        return Optional.of(HttpEndpoint.create("http://www.google.com:80"));
    } else {
        return Optional.empty();
    }
});

```

Register class extending `AbstractResolver<HttpEndpoint>`:

```java
class SampleEndpointResolver extends AbstractResolver<HttpEndpoint> {
    String name() {
        return "SampleEndpointResolver";
    }

    Optional<HttpEndpoint> resolve(svcName: String, env: Environment) { 
        if ("sample".equals(svcName)) {
            return Optional.of(Endpoint.create("http://pekko.io:80"));
        } else if ("google".equals(svcName))
            return Optional.of(Endpoint.create("http://www.google.com:80"));
        } else {
            return Optional.empty();
        }
    }
}    

// Register EndpointResolver
ResolverRegistry.get(system).register(HttpEndpoint.class, new SampleEndpointResolver());
```

You can register multiple `Resolver`s.  The chain is executed in the reverse order of registration.  If a resolver returns `None`, it means it could not resolve it and the next resolver is tried.  

If the resolved endpoint is a secure one, e.g., https, an `SSLContext` can be passed to `HttpEndpoint` as an optional parameter.

An optional `Config` can also be passed to `HttpEndpoint` to override the default configuration.  However, the client specific configuration has higher precedence over the passed in configuration.

Please see [Resource Resolution](resolver.md) for details on resolution in general.

### Per Client Configuration

[pekko HTTP Configuration](http://doc.pekko.io/docs/pekko-http/current/scala/http/configuration.html) defines the default values for configuration.  You can override these defaults in `application.conf`; however, that would affect all the clients.  To do a client specific override, pekko HTTP allows passing a `ConnectionPoolSettings` during `HostConnectionPool` flow creation. This is supported by squbs as well.

In addition to the above, squbs allows a client specific override in `application.conf`.  You just need to specify a configuration section with the client's name that has `type = squbs.httpclient`.  Then, you can specify any client configuration inside the section.  For instance, if we would like to override the `max-connections` setting only for the above `"sample"` client, but no other client, we can do it as follows: 

```
sample {
  type = squbs.httpclient
  
  pekko.http.host-connection-pool {
    max-connections = 10
  }
}
```

### Pipeline

We often need to have common infrastructure functionality or organizational standards across different clients.  Such infrastructure includes, but is not limited to, logging, metrics collection, request tracing, authentication/authorization, tracking, cookie management, A/B testing, etc.  As squbs promotes separation of concerns, such logic would belong to infrastructure and not client implementation. The [squbs pipeline](pipeline.md) allows infrastructure to provide components installed into a client without the client owner having to worry about such aspects.  Please see [squbs pipeline](pipeline.md) for more details.

Generally speaking, a pipeline is a Bidi Flow acting as a bridge in between squbs client and the pekko HTTP layer.  `squbs-httpclient` allows registering a Bidi pekko Streams flow globally for all clients (default pipeline) or for individual clients.  To register a client specific pipeline, set the `pipeline` configuration.  You can turn on/off the default pipeline via `defaultPipeline` setting (it is set to `on`, if not specified):   

```
sample {
  type = squbs.httpclient
  pipeline = metricsFlow
  defaultPipeline = on
}
```

Please see [squbs pipeline](pipeline.md) to find out how to create a pipeline and configure default pipeline.

### Metrics

squbs comes with pre-built [pipeline](#pipeline) elements for metrics collection and squbs giter8 templates sets those as default.  Accordingly, each squbs http client is enabled to collect [Codahale Metrics](http://metrics.dropwizard.io/3.1.0/getting-started/) out-of-the-box without any code change or configuration.  Please note, squbs metrics collection does NOT require AspectJ or any other runtime code weaving.  The following metrics are available on JMX by default:

   * Request Timer
   * Request Count Meter
   * Response Count Meter
   * A meter for each http response status code category: 2xx, 3xx, 4xx, 5xx
   * A meter for each exception type that was returned by `ClientFlow`.


You can access the `MetricRegistry` by `MetricsExtension(system).metrics`.  This allows you to create further meters, timers, histograms, etc or to pass it to a different type metrics reporter.

### JMX Beans

Visibility of the system configuration has utmost importance while trouble shooting an issue.  `squbs-httpclient` registers a JMX bean for each client.  The JMX bean exposes all the configuration, e.g., endpoint, host connection pool settings, etc.  The name of the bean is set as `org.squbs.configuration.${system.name}:type=squbs.httpclient,name=$name`.  So, if the actor system name is `squbs` and the client name is `sample`, then the name of the JMX bean would be `org.squbs.configuration.squbs:type=squbs.httpclient,name=sample`.

### Circuit Breaker

squbs provides `CircuitBreakerBidi` pekko Streams `GraphStage` to provide circuit breaker functionality for streams.  It is a generic circuit breaker implementation for streams.  Please see [Circuit Breaker](circuitbreaker.md) documentation for details.

Circuit Breaker might potentially change the order of messages, so it requires a `Context` to be carried around, like `ClientFlow`.  But, in addition to that, it needs to be able to uniquely identify each element for its internal mechanics.  Accordingly, the `Context` passed to `ClientFlow` or a mapping from `Context` should be able to uniquely identify each element.  If circuit breaker is enabled, and the `Context` passed to `ClientFlow` does not uniquely identify each element, then you will experience unexpected behavior.  Please see [Context to Unique Id Mapping](circuitbreaker.md#context-to-unique-id-mapping) section of Circuit Breaker documentation for details on providing a unique id.

Circuit Breaker is disabled by default.  Please see [Configuring in application.conf](#configuring-in-applicationconf) and [Passing CircuitBreakerSettings Programmatically](#passing-circuitbreakersettings-programmatically) sections below for enabling.

Once enabled, by default, any `Failure` or a `Success` with http status code `400` or greater increments the failure count.  The default `CircuitBreakerState` implementation is `AtomicCircuitBreakerState`, which can be shared across materializations and across flows.  These can be customized by [Passing CircuitBreakerSettings Programmatically](#passing-circuitbreakersettings-programmatically).

##### Configuring in application.conf

Inside the client specific configuration, you can add `circuit-breaker` and specify the configuration you would like to override.  For the rest, it will use the default settings.  Please see [here](../squbs-ext/src/main/resources/reference.conf) for the default circuit breaker configuration.

```
sample {
  type = squbs.httpclient

  circuit-breaker {
    max-failures = 2
    call-timeout = 10 milliseconds
    reset-timeout = 100 seconds
  }
}
```

##### Passing CircuitBreakerSettings Programmatically

You can pass a `CircuitBreakerSettings` instance with the programmatically.  The API lets you to pass a custom `CircuitBreakerState` and optional  fallback, failure decider and unique id mapper functions.  If a `CircuitBreakerSettings` instance is passed in programmatically, then the circuit breaker settings in `application.conf` are ignored.

In below examples a fallback response is provided via `withFallback`.  The default failure decider is overriden via `withFailureDecider` to consider only status codes `>= 500` to increment failure count of circuit breaker:

###### Scala

```scala
import org.squbs.streams.circuitbreaker.CircuitBreakerSettings

val circuitBreakerSettings =
  CircuitBreakerSettings[HttpRequest, HttpResponse, Int](circuitBreakerState)
    .withFallback( _ => Try(HttpResponse(entity = "Fallback Response")))
    .withFailureDecider(
      response => response.isFailure || response.get.status.intValue() >= StatusCodes.InternalServerError.intValue)
    
val clientFlow = ClientFlow[Int]("sample", circuitBreakerSettings = Some(circuitBreakerSettings))    
```

###### Java

```java
import org.squbs.streams.circuitbreaker.japi.CircuitBreakerSettings;

final CircuitBreakerSettings circuitBreakerSettings =
        CircuitBreakerSettings.<HttpRequest, HttpResponse, Integer>create(circuitBreakerState)
                .withFallback(httpRequest -> Success.apply(HttpResponse.create().withEntity("Fallback Response")))
                .withFailureDecider(response ->
                        response.isFailure() || response.get().status().intValue() >= StatusCodes.INTERNAL_SERVER_ERROR.intValue());
```

##### Calling another service as a fallback

A common scenario in circuit breaker usage is to call another service as the fallback.  Calling another service would require a new stream materialization within the fallback function; so, we suggest users to get the fail fast message downstream instead and branch off accordingly.  This lets defining the fallback `ClientFlow` within the same stream.

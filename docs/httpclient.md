##Overview

squbs HttpClient is the library enabling Scala/Akka/Spray applications to easily execute HTTP requests and asynchronously process the HTTP responses in a managed environment. This means it provides environment awareness whether you're in development or in production, service routing which exact service endpoint should be accessed, and also service resilience ensuring that service quality is being maintained. It is build on the top of Akka and the spray-can layer.

##Concepts

###HttpClient
The HttpClient is primary interface for the application to call a web site or service. For ease of use and manageability, the following facilities are provided with the HttpClient:

* json4s native/jackson Marshalling/Unmarshalling.
* Per-client configuration to easy support spray related configuration, SslContext and so on.
* HttpClient mark-up and mark-down.
* A HttpClientJMXBean to expose HttpClient/EndpointResolver/EnvironmentResolver and mark-up/mark-down state information.

###EndpointResolver
The EndpointResolver is a resolver that maps a logical request to an endpoint. Generally, a service or web request on the internet does not be mapped. But a similar request inside an organization will need to be mapped based on a particular environment. For instance, a request to http://www.ebay.com/itm/0001 will be mapped to http://www.qa.ebay.com/itm/001 in the development and QA environments so as not to hit production data.

###EndpointRegistry
The EndpointRegistry is a registry keeping a sequence of resolvers and allowing resolvers to be registered at initialization time. This keeps the application code for resolution concise, easy to read, and easy to maintain. Resolvers will be tried one-by-one to map a request. This falls back to a default resolver that takes the request URL as is without modifications. 

###EnvironmentResolver
The EnvironmentResolver is a pluggable resolver that determines the environment of the endpoint to be called.

###EnvironmentRegistry
The EnvironmentRegistry keeps a sequence of EnvironmentResolvers, defaulting to a default resolver that does not resolve the environment allowing making straight calls.

###Pipeline
An infrastructure managing a sequence of pluggable handlers that would process requests/responses in sequence. From an HttpClient perspective, a request would be passed through the request pipeline which could in turn decorate the request or keep counters before sending the request out. In turn, the response also would be passed to the response pipeline to pre-process responses before handing the response to the application.

###Pipeline Handlers
Request/response handlers plugged into the pipeline. One example of a pipeline handler plugging into both the request and response pipeline is a tracing handler that adds a tracing request header to the request and reads the the tracing response header from the response. Assuming the endpoint also supports tracing, this would essentially allow service calls across multiple tiers to be stitched together for tracing and monitoring purposes.

Following are a few basic pipeline handlers provided with the HttpClient

* RequestCredentialsHandler
* RequestHeaderHandler
* ResponseHeaderHandler

## Getting Started

### Dependencies

Add the following dependency to your build.sbt or scala build file:

"org.squbs" %% "httpclient" % squbsVersion

### EndpointRegistry

Users could register multiple `EndpointResolver`s with the `EndpointRegistry`. The sequence of EndpointResolver to be tried is the reverse of the registration sequence. In other words, the last EndpointResolver registered has the highest priority and will be consulted first. If the endpoint cannot be resolved, the next to last EndpointResolve registered will be tried, in sequence. If the service name starts with "http://" or "https://" and is not resolved by any registered resolver, the service name itself will be used as the endpoint. This allows users to use a well known endpoint directly without any resolver registration, yet allows the resolver to make proper translations as needed.

The following is an example of an EndpointResolver:

```scala
object DummyLocalhostResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Environment = Default): Option[Endpoint] = {
    if (svcName == null && svcName.length <= 0) throw new HttpClientException(700, "Service name cannot be null")
    env match {
      case Default | DEV => Some(Endpoint("http://localhost:8080/" + svcName))
      case _   => throw new HttpClientException(701, "DummyLocalhostResolver cannot support " + env + " environment")
    }
  }
  override def name: String = "DummyLocalhostResolver"
}

```
 
After defining the resolver, simply register the resolver with the EndpointRegistry:

```scala
EndpointRegistry.register(DummyLocalhostResolver)

```

### EnvironmentRegistry

The `EnvironmentRegistry` is used to resolve 'Default' environment to a particular environment such as 'Dev', 'QA' and 'Prod'. Users could register multiple EnvironmentResolvers. Similar to the EndpointResolver, The sequence of EnvironmentResolvers to be tried is the reverse of the registration sequence. If the environment cannot be resolved, it will fallback to 'Default'.

The following shows an example of an EnvironmentResolver:

```scala

object DummyPriorityEnvironmentResolver extends EnvironmentResolver {

  override def resolve(svcName: String): Option[Environment] = svcName match {
    case "abc" => Some(QA)
    case _ => None
  }

  override def name: String = "DummyPriorityEnvironmentResolver"
}

``` 
And here is how to register an EnvironmentResolver:

```scala

EnvironmentRegistry.register(DummyPriorityEnvironmentResolver)

```

### HttpClient API

#### Get Or Create HttpClient

```java
val client: HttpClient = HttpClientFactory.getOrCreate(name: String, env: Environment = Default, pipeline: Option[Pipeline] = None)
```

- name(Mandatory): Service Name
- env(Optional): Service Call Environment, by default is Default
- pipeline(Optional): Service Call Request/Response Pipeline

#### Update

Update HttpClient Configuration. Note this will create a new HttpClient instance. The created client is immutable:

```java
val client: HttpClient = HttpClientFactory.getOrCreate(name: String, env: Environment = Default, pipeline: Option[Pipeline] = None).withConfig(config: Configuration)
val response: Future[HttpResponseWrapper] = client.get(uri: String)
```

#### MarkDown

```java
client.markDown
```

#### MarkUp

```java
client.markUp
```

#### Use HttpClient Make HTTP Call

```java
val response: Future[HttpResponseWrapper] = client.get(uri: String)
```
- uri(Mandatory): Uri for Service Call

```java
val response: Future[HttpResponseWrapper] = client.post[T](uri: String, content: Some[T])
```
- uri(Mandatory): Uri for Service Call
- content(Mandatory): Post Content

```java
val response: Future[HttpResponseWrapper] = client.put[T](uri: String, content: Some[T])
```
- uri(Mandatory): Uri for Service Call
- content(Mandatory): Put Content

```java
val response: Future[HttpResponseWrapper] = client.head(uri: String)
```
- uri(Mandatory): Uri for Service Call

```java
val response: Future[HttpResponseWrapper] = client.delete(uri: String)
```
- uri(Mandatory): Uri for Service Call

```java
val response: Future[HttpResponseWrapper] = client.options(uri: String)
```
- uri(Mandatory): Uri for Service Call

#### Use HttpClient Make HTTP Call and return Unmarshalled Object

User needs to implement the Json Serialize/Deserialize Protocol, please see [json4s Marshalling/Unmarshalling](#json4s-marshallingunmarshalling).

```java
val response: Future[HttpResponseEntityWrapper[R]] = client.getEntity[R](uri: String)
```
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = client.postEntity[T, R](uri: String, content: Some[T])
```
- uri(Mandatory): Uri for Service Call
- T(Mandatory): Post Content
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = client.putEntity[T, R](uri: String, content: Some[T])
```
- uri(Mandatory): Uri for Service Call
- T(Mandatory): Put Content
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = client.headEntity[R](uri: String)
```
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = client.deleteEntity[R](uri: String)
```
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = client.optionsEntity[R](uri: String)
```
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object


### Pipeline

The pipeline is a way for pre-processing the request as well as post-processing the response. The user can provide a request/response pipeline when calling service as follows:
 
```java
object DummyRequestResponsePipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("req2-name", "req2-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("res2-name", "res2-value")).processResponse)
}
```

By default, squbs HttpClient provides the below Request/Response Handlers.
- RequestCredentialsHandler (HttpCredentials Related)
- RequestAddHeaderHandler (Add Header in Request Phase)
- RequestRemoveHeaderHandler (Remove Header in Request Phase)
- RequestUpdateHeaderHandler (Update Header in Request Phase)
- ResponseAddHeaderHandler (Add Header in Response Phase)
- ResponseRemoveHeaderHandler (Remove Header in Response Phase)
- ResponseUpdateHeaderHandler (Update Header in Response Phase)

### Configuration

Configuration provides spray host related configuration and sslContext configuration.

Configuration
- hostSettings (default is Configuration.defaultHostSettings)
- connectionType (default is ClientConnectionType.AutoProxied)
- sslContext (default is None)

### Json4s Marshalling/Unmarshalling

Squbs HttpClient provides integration with Json4s Marshalling/Unmarshalling to support native/jackson Protocols.

Json4s Jackson Support:

- Json4sJacksonNoTypeHintsProtocol (NoTypeHints, user only need to import this object)
- Json4sJacksonShortTypeHintsProtocol (ShortTypeHints, user need to implement the trait)
- Json4sJacksonFullTypeHintsProtocol (FullTypeHints, user need to implement the trait)
- Json4sJacksonCustomProtocol (Customized, user need to implement the trait)

Json4s Native Support:

- Json4sNativeNoTypeHintsProtocol (NoTypeHints, user only need to import this object)
- Json4sNativeShortTypeHintsProtocol (ShortTypeHints, user need to implement the trait)
- Json4sNativeFullTypeHintsProtocol (FullTypeHints, user need to implement the trait)
- Json4sNativeCustomProtocol (Customized, user need to implement the trait)

### HttpClientJMXBean

HttpClientInfoMXBean:
- name
- env
- endpoint
- status
- connectionType
- maxConnections
- maxRetries
- maxRedirects
- requestTimeout
- connectingTimeout
- requestPipelines
- responsePipelines

```java
val httpClients: java.util.List[HttpClientInfo] = HttpClientBean.getHttpClientInfo
```

EndpointresolverMXBean:
- position
- resolver

```java
val endpointResolvers: java.util.List[EndpointResolverInfo] = EndpointResolverBean.getHttpClientEndpointResolverInfo
```

EnvironmentResolverBean:
- position
- resolver

```java
val environmentResolvers: java.util.List[EnvironmentResolverInfo] = EnvironmentResolverBean.getHttpClientEnvironmentResolverInfo
```
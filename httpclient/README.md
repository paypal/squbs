## Overview

Squbs Http Client is the library to allow scala applications to easily execute HTTP requests and asynchronously process the HTTP responses. It build on the top of spray client layer.

## Features
Squbs Http Client provides the following additional features on top of spray client layer:

1. Provide RoutingDefinition to generate different endpoint base on service name & env.
2. Provide RoutingRegistry support multiple RoutingDefinition to resolve ednpoint for service.
3. Provide PipelineDefinition to execute request/response pipeline.
4. Provide Configuration to easy support spray related configuration, retry function and so on.
5. Provide JMXBean to expose httpclient related information.
6. Provide json4s native/jackson Marshalling/Unmarshalling.
7. Provide markup/markdown feature for HttpClient
8. Provide some basic pipeline handlers.	
   1) RequestCredentialsHandler
   2) RequestHeaderHandler
   3) ResponseHeaderHandler

## How to Use

### Dependencies

Add below dependencies on your build.sbt or related scala build file

"org.squbs" % "squbs-httpclient" % "0.1.0-SNAPSHOT"

### RoutingRegistry

RoutingRegistry is used to generate different endpoint base on service name & env. User could registry multiple RoutingDefinition, it based on the sequence when user registry their RoutingDefinition, the latter one will take the priority if it could resolved. If it cannot resolve, but the service name is start with "http://" or "https://", it will fallback to the svcName as the endpoint, this is simple to use base on the third party service endpoint no matter what kind of env, it will return the same endpoint.

```java

class GoogleRoutingDefinition extends RoutingDefinition {
  override def resolve(svcName: String, env: Option[String]): Option[String] = {
    if (svcName == name)
      env match {
        case None => Some("http://localhost:8080/maps")
        case Some(env) if env.toUpperCase == "QA" => Some("http://qa.maps.googleapis.com/maps")
        case Some(env) if env.toUpperCase == "PROD" => Some("http://maps.googleapis.com/maps")
		case _ => None
      }

    else
      None
  }

  override def name: String = "googlemap"
}

``` 

```java

RoutingRegistry.register(new GoogleRoutingDefinition())

```

### Service Call API

#### Get Or Create HttpClient

```java
val httpClient: HttpClient = HttpClientFactory.getOrCreate(name: String, env: Option[String] = None, config = Option[Configuration] = None, pipelineDefinition = Option[PipelineDefinition] = None)
```

- name(Mandatory): Service Name
- env(Optional): Service Call Environment
- config(Optional): Service Call Configuration
- pipelineDefinition(Optional): Service Call Request/Response Pipeline

#### Update

```java
val updatedHttpClient: HttpClient = httpClient.update(config = Option[Configuration] = None, pipelineDefinition = Option[PipelineDefinition] = None)
```

#### Use HttpClient Make HTTP GET

```java
val response:Future[HttpResponseWrapper] = httpClient.get(uri: String)
```
- uri(Mandatory): Uri for Service Call

#### Use HttpClient Make HTTP POST

```java
val response:Future[HttpResponseWrapper] = httpClient.post[T](uri: String, content: Some[T])
```
- uri(Mandatory): Uri for Service Call
- content(Mandatory): Post Content

#### Use HttpClient Make HTTP PUT

```java
val response:Future[HttpResponseWrapper] = httpClient.put[T](uri: String, content: Some[T])
```
- uri(Mandatory): Uri for Service Call
- content(Mandatory): Put Content

#### Use HttpClient Make HTTP HEAD

```java
val response:Future[HttpResponseWrapper] = httpClient.head(uri: String)
```
- uri(Mandatory): Uri for Service Call

#### Use HttpClient Make HTTP DELETE

```java
val response:Future[HttpResponseWrapper] = httpClient.delete(uri: String)
```
- uri(Mandatory): Uri for Service Call


#### Use HttpClient Make HTTP Options

```java
val response:Future[HttpResponseWrapper] = httpClient.options(uri: String)
```
- uri(Mandatory): Uri for Service Call

#### Use HttpClient Make HTTP GET and return Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = httpClient.getEntity[R](uri: String)
```
User need to implement the Json Serialize/Deserialize Protocol, please see json4s Marshalling/Unmarshalling Section.
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object


```java
val response: Future[HttpResponseEntityWrapper[R]] = httpClient.postEntity[T, R](uri: String, content: Some[T])
```
User need to implement the Json Serialize/Deserialize Protocol, please see json4s Marshalling/Unmarshalling Section.
- uri(Mandatory): Uri for Service Call
- T(Mandatory): Post Content
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = httpClient.putEntity[T, R](uri: String, content: Some[T])
```
User need to implement the Json Serialize/Deserialize Protocol, please see json4s Marshalling/Unmarshalling Section.
- uri(Mandatory): Uri for Service Call
- T(Mandatory): Put Content
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = httpClient.headEntity[R](uri: String)
```
User need to implement the Json Serialize/Deserialize Protocol, please see json4s Marshalling/Unmarshalling Section.
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = httpClient.deleteEntity[R](uri: String)
```
User need to implement the Json Serialize/Deserialize Protocol, please see json4s Marshalling/Unmarshalling Section.
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object

```java
val response: Future[HttpResponseEntityWrapper[R]] = httpClient.optionsEntity[R](uri: String)
```
User need to implement the Json Serialize/Deserialize Protocol, please see json4s Marshalling/Unmarshalling Section.
- uri(Mandatory): Uri for Service Call
- R(Mandatory): Unmarshall Object

### Service Call Message Based API

#### Create HttpClient

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! CreateHttpClientMsg(name: String, env: Option[String] = None, config: Option[Configuration] = None, pipelineDefinition: Option[PipelineDefinition] = None)
``` 
- name(Mandatory): Service Name
- env(Optional): Service Call Environment
- config(Optional): Service Call Configuration
- pipelineDefinition(Optional): Service Call Request/Response Pipeline

Response:
- Success => CreateHttpClientSuccessMsg(hc:IHttpClient)
- Failure => CreateHttpClientFailureMsg(e:HttpClientExistException)

#### Update HttpClient

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! UpdateHttpClientMsg(name: String, env: Option[String] = None, config: Option[Configuration] = None, pipelineDefinition: Option[PipelineDefinition] = None)
``` 
- name(Mandatory): Service Name
- env(Optional): Service Call Environment
- config(Optional): Service Call Configuration
- pipelineDefinition(Optional): Service Call Request/Response Pipeline

Response:
- Success => UpdateHttpClientSuccessMsg(hc:IHttpClient)
- Failure => UpdateHttpClientFailureMsg(e:HttpClientNotExistException)

#### Delete HttpClient

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! DeleteHttpClientMsg(name: String, env: Option[String] = None)
```
- name(Mandatory): Service Name
- env(Optional): Service Call Environment

Response:
- Success => DeleteHttpClientSuccessMsg(hc:IHttpClient)
- Failure => DeleteHttpClientFailureMsg(e:HttpClientNotExistException)

#### Delete All HttpClients

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! DeleteAllHttpClientMsg
```

Response:
- Success => DeleteAllHttpClientSuccessMsg(emptyMap:TrieMap[(String, Option[String]), IHttpClient])

#### Get HttpClient

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! GetHttpClientMsg(name: String, env: Option[String] = None)
```
- name(Mandatory): Service Name
- env(Optional): Service Call Environment

Response:
- Success => GetHttpClientSuccessMsg(hc:IHttpClient)
- Failure => GetHttpClientFailureMsg(e:HttpClientNotExistException)

#### Get All HttpClients

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! GetAllHttpClientMsg
```
Response:
- Success => GetAllHttpClientSuccessMsg(map:TrieMap[(String, Option[String]), IHttpClient])

#### HttpClient Make HTTP GET Call

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! HttpClientGetCallMsg(name: String, env: Option[String] = None, httpMethod: HttpMethod, uri: String)
```
- name(Mandatory): Service Name
- env(Optional): Service Call Environment
- httpMethod(Mandatory): Only support GET/DELETE/HEAD/OPTIONS
- uri(Mandatory): Service Uri

Response:
- Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
- Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
- Failure => HttpClientGetCallFailureMsg(e: HttpClientNotExistException)
- Failure => HttpClientGetCallFailureMsg(e: HttpClientNotSupportMethodException)

#### HttpClient Make HTTP POST Call

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! HttpClientPostCallMsg[T](name: String, env: Option[String] = None, httpMethod: HttpMethod, uri: String, content: Some[T])
```
- name(Mandatory): Service Name
- env(Optional): Service Call Environment
- httpMethod(Mandatory): Only support GET/DELETE/HEAD/OPTIONS
- uri(Mandatory): Service Uri
- T(Mandatory): Unmarshall object
- content: POST content

Response:
- Success => HttpResponseWrapper(status: StatusCode, content: Right[HttpResponse])
- Failure => HttpResponseWrapper(status: StatusCode, content: Left[Throwable])
- Failure => HttpClientPostCallFailureMsg(e: HttpClientNotExistException)
- Failure => HttpClientPostCallFailureMsg(e: HttpClientNotSupportMethodException)

### PipelineDefinition

PipelineDefinition provide a way for user to provide request/response pipeline when calling service.

e.g.
 
```java
object RequestResponsePipeline extends PipelineDefinition {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("request1-name", "request1-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("response1-name", "response1-value")).processResponse)
}
```

By default, squbs Http Client provide the below Request/Response Handlers.
- RequestCredentialsHandler (HttpCredentials Related)
- RequestAddHeaderHandler (Add Header in Request Phase)
- RequestRemoveHeaderHandler (Remove Header in Request Phase)
- RequestUpdateHeaderHandler (Update Header in Request Phase)
- ResponseAddHeaderHandler (Add Header in Response Phase)
- ResponseRemoveHeaderHandler (Remove Header in Response Phase)
- ResponseUpdateHeaderHandler (Update Header in Response Phase)

### Configuration

Configuration provides spray host related configuration and some basic configurations.

ServiceConfiguration
- maxRetryCount (default is 0)
- serviceTimeout (default is 1 seconds)
- connectionTimeout (default is 1 seconds)

HostConfiguration
- hostSettings (default is None)
- connectionType (default is ClientConnectionType.AutoProxied)

### Json4s Marshalling/Unmarshalling

Squbs Http Client provide integration with Json4s Marshalling/Unmarshalling to support native/jackson Protocols.

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

### JMXBean

User could use the below code to get all HttpClients, please make sure the bean has been registry.

```java
val httpClients = HttpClientBean.getInfo
```

### MarkDown/MarkUp HttpClient

Squb HttpClient could be easy markup/markdown by the user.

#### Service API to MarkDown/MarkUp

```java
httpClient.markDown
httpClient.markUp
```

#### Service Message Based API to MarkDown/MarkUp

##### MarkDown HttpClient

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! MarkDownHttpClientMsg(name: String, env: Option[String] = None)
```
- name(Mandatory): Service Name
- env(Optional): Service Call Environment

Response:
- Success => MarkDownHttpClientSuccessMsg(hc:IHttpClient)
- Failure => MarkDownHttpClientFailureMsg(e:HttpClientNotExistException)

##### MarkUp HttpClient

```java
val httpClientManager: HttpClientManager = HttpClientManager(system).httpClientManager
httpClientManager ! MarkUpHttpClientMsg(name: String, env: Option[String] = None)
```
- name(Mandatory): Service Name
- env(Optional): Service Call Environment

Response:
- Success => MarkUpHttpClientSuccessMsg(hc:IHttpClient)
- Failure => MarkUpHttpClientFailureMsg(e:HttpClientNotExistException)
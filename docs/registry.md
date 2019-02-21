
# The Actor Registry

## Overview

The actor registry provides squbs applications an easy way to send/receive message to squbs well-known actors, especially across cubes. This provides an additional layer of abstraction across cubes allowing actors to find other actors without knowing much about them. The actor registry also acts as a facade that may manage access control, security, timeouts across modules or even simulate non-actor systems as external actors.

### Concepts

* Well-known actors are actors registered with the cube throught `squbs-meta.conf` as described in [Unicomplex & Cube Bootstrapping](bootstrap.md).
* `ActorLookup` API for Scala and `japi.ActorLookup` API for Java is used to send/receive message to well-known actors.
* `ActorRegistry` is the common façade actor holding all well-known actor information. 

## Dependencies

To use the actor registry, add the following dependency to your build.sbt or scala build file:

```
"org.squbs" %% "squbs-actorregistry" % squbsVersion
```

## Well-Known Actor

Squbs well-known actors are defined at the squbs-actors section of `META-INF/squbs-meta.conf` as described in [Unicomplex & Cube Bootstrapping](bootstrap.md). The actor registry extends that registration and provides further metadata describing the message types this actor may consume and return:

* class-name:		class name of the actor
* name:			registered name of the actor
* message-class:	request/response message types

Sample registration with extended type information:

```
cube-name = org.squbs.TestCube
cube-version = "0.0.5"
squbs-actors = [
    {
      class-name = org.squbs.testcube.TestActor
      name = TestActor
      message-class = [
        {
          request = org.squbs.testcube.TestRequest
          response= org.squbs.testcube.TestResponse
        }
        {
          request = org.squbs.testcube.TestRequest1
          response= org.squbs.testcube.TestResponse1
        }
      ]
    }
]
```

## Scala API & Samples

* Send message (!/?/tell/ask) to an actor which registered its request message class type as "TestRequest"

  ```scala
  implicit val refFactory : ActorRefFactory = ...
  ActorLookup ! TestRequest(...)
  ```

* Send message (!/?/tell/ask) to an actor which registered its request message class type as "TestRequest", and response message class type as "TestResponse"

  ```scala
  implicit val refFactory : ActorRefFactory = ...
  ActorLookup[TestResponse] ! TestRequest(...)
  ```

* Send message (!/?/tell/ask) to actor which registered its name as "TestActor", and request message class type as "TestRequest"

  ```scala
  implicit val refFactory : ActorRefFactory = ...
  ActorLookup("TestActor") ! TestRequest(...)
  ```

* Send message (!/?/tell/ask) to actor which registered name as "TestActor", request message class type as "TestRequest", and response message class type as "TestResponse"

  ```scala
  implicit val refFactory : ActorRefFactory = ...
  ActorLookup[TestResponse]("TestActor") ! TestRequest(...)  
  ```

* Resolve to actorRef which registered its response message class type as "TestResponse"

  ```scala
  implicit val refFactory : ActorRefFactory = ...
  implicit val timeout : Timeout = ...
  ActorLookup[TestResponse].resolveOne
  ```

* Resolve to actorRef which registered its name as "TestActor"  

  ```scala
  implicit val refFactory : ActorRefFactory = ...
  implicit val timeout : Timeout = ...
  ActorLookup("TestActor").resolveOne
  ```
  
* Resolve to actorRef which registered its name as "TestActor", and response message class type as "TestReponse" 

  ```scala
  implicit val refFactory : ActorRefFactory = ...
  implicit val timeout : Timeout = ...
  ActorLookup[TestReponse]("TestActor").resolveOne
  ```
  
* Resolve to actorRef which registered its name as "TestActor", and request message class type as "TestRequest"
 
  ```scala
  implicit val refFactory : ActorRefFactory = ...
  implicit val timeout : Timeout = ...
  val al= new ActorLookup(requestClass=Some(classOf[TestRequest]))
  al.resolveOne
  ```

## Java API & Samples

* Create your initial ActorLookup object.

  ```java
  // Pass in context() from and Actor or the ActorSystem
  
  private ActorLookup<?> lookup = ActorLookup.create(context());
  ```

* Send message (tell/ask) to an actor which registered its request message class type as "TestRequest"

  ```java
  lookup.tell(new TestRequest(...), self());
  ```

* Send message (tell/ask) to an actor which registered its request message class type as "TestRequest", and response message class type as "TestResponse"

  ```java
  lookup.lookup(TestResponse.class).tell(new TestRequest(...), self())
  ```

* Send message (tell/ask) to actor which registered its name as "TestActor", and request message class type as "TestRequest"

  ```java
  lookup.lookup("TestActor").tell(new TestRequest(...), self())
  ```

* Send message (tell/ask) to actor which registered name as "TestActor", request message class type as "TestRequest", and response message class type as "TestResponse"

  ```java
  lookup.lookup("TestActor", TestResponse.class).tell(new TestRequest(...), self())
  ```

* Resolve to actorRef which registered its response message class type as "TestResponse"

  ```java
  lookup.lookup(TestResponse.class).resolveOne(timeout)
  ```

* Resolve to actorRef which registered its name as "TestActor"  

  ```java
  lookup.lookup("TestActor").resolveOne(timeout)
  ```
  
* Resolve to actorRef which registered its name as "TestActor", and response message class type as "TestReponse" 

  ```java
  lookup.lookup("TestActor", TestResponse.class).resolveOne(timeout)
  ```
  
* Resolve to actorRef which registered its name as "TestActor", and request message class type as "TestRequest". This uses the full lookup signature with optional fields using the type `Optional<T>`. So the name and the request class needs to be wrapped into an `Optional` or pass Optional.empty() if it is not part of the query. The response type is always needed. If any response is accepted, set the response type to `Object.class` to identify that any subclass of `java.lang.Object` is good.
 
  ```java
  lookup.lookup(Optional.of("TestActor"), Optional.of(TestRequest.class), Object.class)
  ```

## Response Types

The response type of an actor discovered by ActorLookup response type discovery (when the response type is provided) is maintained across the result of the lookup. While the programmatic response type is less significant on `tell` or `!`, it becomes significant on `ask` or `?`. The return type of an `ask` is generally `Future[Any]` in Scala or `CompletionStage<Object>` in Java. However the return type from an `ask` or `?` on ActorLookup carries the response type when looked up with it. So you'll get a `Future[T]` or `CompletionStage<T>` if you lookup with response type `T` as can be demonstrated in the examples below. No further MapTo is needed:

### Scala

```scala
// In this example, we show full type annotation. The Scala compiler is able
// to infer the type if you just pass one side, i.e. the type parameter at
// ActorLookup, or the type annotation on the val f declaration.

val f: Future[TestResponse] = ActorLookup[TestResponse] ? TestRequest(...)
```

### Java

```java
CompletionStage<TestResponse> f = lookup.lookup(TestResponse.class).ask(new TestRequest(...), timeout)
```

## Error Handling

Unlike with `actorSelection` which will send a message to dead letters, ActorLookup will respond with `ActorNotFound` if the wanted actor is not in the system or not registered. In the Java API, the `ActorNotFound` is usually wrapped in a `java.util.concurrent.ExecutionException` and accessible by `getCause()` on the `ExecutionException`.

## Monitoring

There is JMX Bean created for each well-known actor. It is named `org.squbs.unicomplex:type=ActorRegistry,name=${actorPath}`

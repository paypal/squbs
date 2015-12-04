
#The Actor Registry

##Overview

The actor registry provides squbs applications an easy way to send/receive message to squbs well-known actors, especially across cubes. This provides an additional layer of abstraction across cubes allowing actors to find other actors without knowing much about them. The ActorRegistry also acts as a facade that may manage access control, security, timeouts across modules or even simulate non-actor systems as external actors.

At this time, the Actor Registry is considered experimental. The API is very type-oriented and would not be a good fit for Java users. A Java API will be provided in future releases.

###Concepts

* Well-known actor are actors registered with the cube throught `squbs-meta.conf` as described in [Unicomplex & Cube Bootstrapping](bootstrap.md).
* ActorLookup API is used to send/receive message to well-known actors.
* ActorRegistry is the common façade actor holding all well-known actor information. 


##Well Know Actor

Squbs well know actor is defined at squbs-actors section of `META-INF/squbs-meta.conf` as described in [Unicomplex & Cube Bootstrapping](bootstrap.md). The actor registry extends that registration and provides further metadata describing the message types this actor may consume and return:

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

##ActorLookup API Usage & Samples

* Send message (!/?/tell/ask) to an actor which registered its request message class type as "TestRequest"

  ```
  implicit val system : ActorSystem = ...
  ActorLookup ! TestRequest(...)  			
  ```

* Send message (!/?/tell/ask) to an actor which registered its request message class type as "TestRequest", and response message class type as "TestResponse"

  ```
  implicit val system : ActorSystem = ...
  ActorLookup[TestResponse] ! TestRequest(...)	  
  ```

* Send message (!/?/tell/ask) to actor which registered its name as "TestActor", and request message class type as "TestRequest"

  ```
  implicit val system : ActorSystem = ...
  ActorLookup("TestActor") ! TestRequest(...) 	
  ```

* Send message (!/?/tell/ask) to actor which registered name as "TestActor", request message class type as "TestRequest", and response message class type as "TestResponse"

  ```
  implicit val system : ActorSystem = ...
  ActorLookup[TestResponse]("TestActor") ! TestRequest(...)  
  ```

* Resolve to actorRef which registered its response message class type as "TestResponse"

  ```
  implicit val system : ActorSystem = ...
  implicit val timeout : Timeout = ...
  ActorLookup[TestResponse].resolveOne
  ```

* Resolve to actorRef which registered its name as "TestActor"  

  ```
  implicit val system : ActorSystem = ...
  implicit val timeout : Timeout = ...
  ActorLookup("TestActor").resolveOne
  ```
  
* Resolve to actorRef which registered its name as "TestActor", and response message class type as "TestReponse" 

  ```
  implicit val system : ActorSystem = ...
  implicit val timeout : Timeout = ...
  ActorLookup[TestReponse]("TestActor").resolveOne
  ```
  
* Resolve to actorRef which registered its name as "TestActor", and request message class type as "TestRequest"
 
  ```
  implicit val system : ActorSystem = ...
  implicit val timeout : Timeout = ...
  val al= new ActorLookup(requestClass=Some(classOf[TestRequest]))
  al.resolveOne
  ```

##Monitoring

There is JMX Bean created for each well-known actor. It is named `org.squbs.unicomplex:type=ActorRegistry,name=${actorPath}`



## Dependencies

To use the ActorRegistry, add the following dependency to your build.sbt or scala build file:

```
"org.squbs" %% "squbs-actorregistry" % squbsVersion
```

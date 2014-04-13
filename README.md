
#squbs

Welcome to squbs (pronounced "skewbs")! We will assimilate your technology and culture. Resistance is futile.

squbs is a modular actor-based services container build on top of Scala, Akka, and Spray. At the core, it standardizes Akka and Spray applications for running and being managed in the PaaS/Cloud environment.

squbs also implements a philosophy for complex, modular applications. This philosophy is modeled after social and organizational paradigms dividing code into symbiotic groups or modules called "cubes." Cubes consist of a supervisory actor, well-known actors that can be looked up from outside the cube, and other actors. The supervisory actor is responsible for resilience of the cube. Well-known actors handle or dispatch messages sent to the cube. Cubes and all actors talk to each other asynchronously and work in synergy.


##Definitions


*Unicomplex*: The core of squbs managing the actor system, bootstrapping, and possible actor capability directories and hot upgrading of the cubes.

*Cube*: A self contained module consisting of its own supervisory actor that gets bootstrapped. The cube's supervisory actor is looked up by name (actorFor) or by actorSelection and receives asynchronous messages through its actor interface.

*Actors*: Unit of processing in the Akka actor system. An actor may process messages, forward messages, reply to messages, or do a combination of all the above. Well known actors are registered by name and are visible from all other cubes, including remotely.

*Service*: A cube that exposes itself as a REST service allowing access to functionality provided by the squbs ecosystem.

*Message*: Typed, asynchronous messages sent between actors and cubes.


##Problem

For singular applications, squbs is merely a container that standardizes the application lifecycle in the PaaS/Cloud environment - something the authors of Akka and Spray left for the application to do. It takes minimal amount of work to make an existing Akka/Spray application run on squbs. It also allows for injecting management and monitoring infrastructure into the application.

For complex applications spanning millions of lines of code, such large code bases have a large set of very deep dependencies. These dependencies often conflict between each other making it a challenge to manage these large systems. The code and dependency complexity is also a major hindrance for code to quickly evolve according to business needs. Testing of these large and complex systems become harder as the system grows. Enterprise integration solutions are generally applied to break complex such large systems into smaller, loosely coupled components but the weight of their integration mechanism is often an overkill. squbs is an attempt to provide a high performance asynchronous infrastructure that is lightweight and lends itself to high performance interaction between these loosely coupled cubes as well as within them.

In addition to systematically breaking down code complexity, squbs' fully asynchronous system also lends itself to newer web services technologies such as request/response multiplexing, streaming services, and server-sent events.


#Why do we need squbs?

First and foremost, we need a standard lifecycle management and integration for mission critical, fully monitored the PaaS/Cloud environment. squbs provides these.

Furthermore, Akka itself is a great system that enforces loose coupling. squbs is an attempt to add a modularity layer to Akka allowing actor libraries to be deployed as contained and isolated services. In addition, it also tries to provide implementations of common actor usage patterns allowing the developer to focus on the business logic instead of scopes and lifecycle management.


#Future topics

* Hot upgrades of cubes while taking traffic

* Capability-based dependencies (I need cubes that can do "something" for me - not I need cube "somebody")

* Cube deployment database (I need to recall/upgrade all my cubes version x.y.z. Tell me where they are all deployed.)


#Projects

[squbs](https://github.scm.corp.ebay.com/Squbs/squbs): The core infrastructure for squbs without the eBay operationalization (intended for open source)

[rocksqubs](https://github.scm.corp.ebay.com/Squbs/rocksqubs): The eBay operationalization layer, hardening squbs for handling billions of service requests a day

[squbsamples](https://github.scm.corp.ebay.com/Squbs/squbs-samples): Service and application sample cubes, messages, and services

[sbt-ebay](https://github.scm.corp.ebay.com/Squbs/sbt-ebay): SBT plugin for ebay PaaS integration and operationalization.

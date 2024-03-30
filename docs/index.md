![image](img/squbs-logo-transparent.png)

## Introduction

squbs (pronounced "skewbs") is a suite of components enabling standardization and operationalization of pekko and pekko HTTP applications/services in a large scale, managed, cloud environment. It standardizes how pekko applications are deployed in different environments and how they are hooked up to the operational environments of large, internet-scale organizations.

## squbs Components

1. **Unicomplex**: The micro-container that bootstraps and standardizes the deployment of pekko applications and how they are configured, allowing teams other than PD to understand the configuration and tweak the configuration of applications, partly at runtime, as needed. In addition, the Unicomplex encourages coexistence of different modules, called cubes, and/or operational tooling in a flexible, loosely-coupled fashion that will not incur any code change in order to include new ops tooling or drop out/change some ops tooling. For instance, in cases where we have mixed cloud environments such as private and public cloud needing different operational tools, the same codebase will work with both allowing deployment-time addition of environment-specific tooling.

2. **TestKit**: Used to help test applications written for squbs, or even pekko applications altogether. It provides unit test and small scale load testing facilities that can be run as part of CI.

3. **ZKCluster**: A ZooKeeper-based, datacenter-aware clustering library allowing clustered applications or services to span datacenter and hold the availability characteristics across data centers. This is needed for applications that need intra-cluster communications.

4. **HttpClient**: An operationalized, simplified client that supports both environment and endpoint resolution to fit into different operational environments (QA, Prod) as well as organizational requirements (Topo, direct).

5. **Pattern**: A set of programming patterns and DSLs provided to users.
   1. Orchestration DSL allowing developers to describe their orchestration sequence in an extremely concise manner while running the whole orchestration asynchronously, thus largely simplifying code and reduces latency for the application.
   2. Asynchronous systems depend heavily on timeouts and fixed timeouts are never right. TimeoutPolicy allows users to set policy (like 2.5 sigma) instead of fixed timeout values and takes care of the heuristics by itself allowing systems to adapt to their operating conditions.
   3. Validation provides an [pekko HTTP directive](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/directives/index.html) for data validation by using [Accord Validation Library](http://wix.github.io/accord/).
   4. PersistentBuffer provides a high-performance pekko Streams flow buffer component that persists its content to a memory-mapped file and recovers the content after failure and restart.

6. **ActorRegistry**: A core lookup facility allowing actors of loosely-coupled modules to find each others, or even to model different services as actors.

7. **ActorMonitor**: An add-on operational module that uses JMX to report the stats and behavior of actors in the system. These stats can be seen by any JMX tooling

8. **Pipeline**: An infrastructure allowing sequencing and plugging in of request/response filters. These are used, for instance, for security, rate limiting, logging, etc.
Each of the components have virtually no dependency on each others. They are truly loosely coupled. Developers and organizations are free to pick and choose the components needed for their environment.

9. **Console**: A drop-in module allowing web access to system and application stats through a simple web and service interface returning pretty-printed JSON.

## Getting Started

The easiest way to getting started is to create a project from one of the squbs templates. The followings are currently available giter8 templates:

* [squbs-scala-seed](https://github.com/paypal/squbs-scala-seed.g8): Template for creating sample squbs scala application
* [squbs-java-seed](https://github.com/paypal/squbs-java-seed.g8): Template for creating sample squbs java application

## Documentation

* [Principles of squbs Design](principles_of_the_squbs_design.md)
* [Unicomplex & Cube Bootstrapping](bootstrap.md)
* [Unicomplex Actor Hierarchy](actor-hierarchy.md)
* [Runtime Lifecycle & API](lifecycle.md)
* [Implementing HTTP(S) Services](http-services.md)
* [pekko HTTP Client on Steroids](httpclient.md)
* [Request/Response Pipeline](pipeline.md)
* [Marshalling and Unmarshalling](marshalling.md)
* [Configuration](configuration.md)
* [Testing squbs Applications](testing.md)
* [Clustering squbs Services using ZooKeeper](zkcluster.md)
* [Blocking Dispatcher](blocking-dispatcher.md)
* [Message Guidelines](messages.md)
* [Actor Monitor](monitor.md)
* [Orchestration DSL](orchestration_dsl.md)
* [Actor Registry](registry.md)
* [Admin Console](console.md)
* [Application Lifecycle Management](packaging.md)
* [Resource Resolution](resolver.md)
* pekko Streams `GraphStage`s:
    * [Persistent Buffer](persistent-buffer.md)
    * [Perpetual Stream](streams-lifecycle.md)
    * [Circuit Breaker](circuitbreaker.md)
    * [Timeout](flow-timeout.md)
    * [Deduplicate](deduplicate.md)
* [Timeout Policy](timeoutpolicy.md)
* [Validation](validation.md)

## Release Notes

Please find release notes at [https://github.com/paypal/squbs/releases](https://github.com/paypal/squbs/releases).

## Support & Discussions

Please join the discussion at  [![Join the chat at https://gitter.im/paypal/squbs](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/paypal/squbs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
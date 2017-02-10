![image](docs/img/squbs-logo-transparent.png)

[![Join the chat at https://gitter.im/paypal/squbs](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/paypal/squbs?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/paypal/squbs.svg?branch=master)](https://travis-ci.org/paypal/squbs)
[![Codacy Badge](https://api.codacy.com/project/badge/57368c396cb649c590e4ba678675e55c)](https://www.codacy.com/app/akara-sucharitakul/squbs)
[![Codacy Coverage](https://api.codacy.com/project/badge/coverage/57368c396cb649c590e4ba678675e55c)](https://www.codacy.com/app/akara-sucharitakul/squbs)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.squbs/squbs-unicomplex_2.11/badge.svg?style=flat)](http://search.maven.org/#search|ga|1|g:org.squbs)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

squbs (pronounced "skewbs")is a software container and a suite of components enabling standardization and operationalization of Akka and Spray applications/services in a large scale, managed, cloud environment. It standardizes how Akka/Spray applications are deployed in different environments and how they are hooked up to the operational environments of large, internet-scale organizations.

##squbs Components

1. **Unicomplex**: The micro-container that bootstraps and standardizes the deployment of Akka/Spray applications and how they are configured, allowing teams other than PD to understand the configuration and tweak the configuration of applications, partly at runtime, as needed. In addition, the Unicomplex encourages coexistence of different modules, called cubes, and/or operational tooling in a flexible, loosely-coupled fashion that will not incur any code change in order to include new ops tooling or drop out/change some ops tooling. For instance, in cases where we have mixed cloud environments such as private and public cloud needing different operational tools, the same codebase will work with both allowing deployment-time addition of environment-specific tooling.

2. **TestKit**: Used to help test applications written for squbs, or even Akka applications altogether. It provides unit test and small scale load testing facilities that can be run as part of CI.

3. **ZKCluster**: A ZooKeeper-based, datacenter-aware clustering library allowing clustered applications or services to span datacenter and hold the availability characteristics across data centers. This is needed for applications that need intra-cluster communications.

4. **HttpClient**: An operationalized, simplified client that supports both environment and endpoint resolution to fit into different operational environments (QA, Prod) as well as organizational requirements (Topo, direct).

5. **Pattern**: A set of programming patterns and DSLs provided to users. 
  1. Orchestration DSL allowing developers to describe their orchestration sequence in an extremely concise manner while running the whole orchestration asynchronously, thus largely simplifying code and reduces latency for the application.
  2. Asynchronous systems depend heavily on timeouts and fixed timeouts are never right. TimeoutPolicy allows users to set policy (like 2.5 sigma) instead of fixed timeout values and takes care of the heuristics by itself allowing systems to adapt to their operating conditions.
  3. Spray doesn't have friendly API for Java, the spray.japi package provides a few of Helpers and Factories to help Java developers to construct spray entities easily.
  4. Validation provides a [Spray](http://spray.io) directive for data validation by using [Accord Validation Library](http://wix.github.io/accord/).
  5. PersistentBuffer provides a high-performance Akka Streams flow buffer component that persists its content to a memory-mapped file and recovers the content after failure and restart.

6. **ActorRegistry**: A core lookup facility allowing actors of loosely-coupled modules to find each others, or even to model different services as actors.

7. **ActorMonitor**: An add-on operational module that uses JMX to report the stats and behavior of actors in the system. These stats can be seen by any JMX tooling

8. **Pipeline**: An infrastructure allowing sequencing and plugging in of request/response filters. These are used, for instance, for security, rate limiting, logging, etc.
Each of the components have virtually no dependency on each others. They are truly loosely coupled. Developers and organizations are free to pick and choose the components needed for their environment.

9. **Console**: A drop-in module allowing web access to system and application stats through a simple web and service interface returning pretty-printed JSON.

###Experimental Support for Akka HTTP
The following components are based on Akka HTTP and are considered experimental at this time. These are documented in greater detail under [Experimental Support for Akka HTTP](docs/akka-http-experimental.md).

1. **StreamingPipeline**: Akka streams version of the Pipeline, based upon Akka Streams' `BidiFlow`. It allows users or infrastructure teams to implement request/response filters as `BidiFlow` elements and registering them to the pipeline. This module may only be used if Akka HTTP experimental mode is enabled.

2. **Console for Akka HTTP**: The `squbs-admin-exp` module replaces `squbs-admin` in Akka HTTP experimental mode. It uses the Akka HTTP interfaces instead of the Spray interfaces. In experimental mode, users have to modify their build.sbt and use this dependency instead of squbs-admin.

##Getting Started

The easiest way to getting started is to create a project from one of the squbs templates. The followings are currently available Activator templates:

* [squbs-scala-sample](https://www.lightbend.com/activator/template/squbs-scala-sample): Scala sample application
* [squbs-java-sample](https://www.lightbend.com/activator/template/squbs-java-sample): Java sample application (with only Spray route still Scala)

Also check out these [slightly more advanced samples](https://github.com/paypal/squbs/tree/master/samples).



##Documentation
* [Unicomplex & Cube Bootstrapping](docs/bootstrap.md)
* [Unicomplex Actor Hierarchy](docs/actor-hierarchy.md)
* [Runtime Lifecycle & API](docs/lifecycle.md)
* [Implementing HTTP(S) Services](docs/http-services.md)
* [Accessing Other Services using HTTP or HTTPS](docs/httpclient.md)
* [Marshalling and Unmarshalling](docs/marshalling.md)
* [Configuration Reference](docs/configuration.md)
* [Testing squbs Applications](docs/testing.md)
* [Clustering squbs Services using ZooKeeper](docs/zkcluster.md)
* [The Blocking Dispatcher for Blocking API calls](docs/blocking-dispatcher.md)
* [Message Guidelines](docs/messages.md)
* [Monitoring Actors at Runtime](docs/monitor.md)
* [Using the Orchestration DSL](docs/orchestration_dsl.md)
* [The ActorRegistry](docs/registry.md)
* [Timeout Policy](docs/timeoutpolicy.md)
* [Validation](docs/validation.md)
* [Persistent Buffer](docs/persistent-buffer.md)
* [Console](docs/console.md)
* [Application Lifecycle Management](docs/packaging.md)
* [Streams Lifecycle](docs/streams-lifecycle.md)
* [Experimental Support for Akka HTTP](docs/akka-http-experimental.md)
* [Streaming Pipeline](docs/streamingpipeline.md)
* [Resource Resolution](docs/resolver.md)

##Contributing to squbs
Thank you very much for contributing to squbs. Please read the [contribution guidelines](CONTRIBUTING.md) for the process.

##License
squbs is licensed under the [Apache License, v2.0](LICENSE.txt)

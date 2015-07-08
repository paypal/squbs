#squbs

squbs (pronounced "skewbs")is a software container and a suite of components enabling standardization and operationalization of Akka and Spray applications/services in a large scale, managed, cloud environment. It standardizes how Akka/Spray applications are deployed in different environments and how they are hooked up to the operational environments of large, internet-scale organizations.

##squbs Components

1. **Unicomplex**: The micro-container that bootstraps and standardizes the deployment of Akka/Spray applications and how they are configured, allowing teams other than PD to understand the configuration and tweak the configuration of applications, partly at runtime, as needed. In addition, the Unicomplex encourages coexistence of different modules, called cubes, and/or operational tooling in a flexible, loosely-coupled fashion that will not incur any code change in order to include new ops tooling or drop out/change some ops tooling. For instance, in cases where we have mixed cloud environments such as private and public cloud needing different operational tools, the same codebase will work with both allowing deployment-time addition of environment-specific tooling.

2. **TestKit**: Used to help test applications written for squbs, or even Akka applications altogether. It provides unit test and small scale load testing facilities that can be run as part of CI.

3. **ZKCluster**: A ZooKeeper-based, datacenter-aware clustering library allowing clustered applications or services to span datacenter and hold the availability characteristics across data centers. This is needed for applications that need intra-cluster communications.

4. **HttpClient**: An operationalized, simplified client that supports both environment and endpoint resolution to fit into different operational environments (QA, Prod) as well as organizational requirements (Topo, direct).

5. **Pattern**: A set of programming patterns and DSLs provided to users. The prominent one at this point is the Orchestration DSL allowing developers to describe their orchestration sequence in an extremely concise manner while running the whole orchestration asynchronously, thus largely simplifying code and reduces latency for the application.

6. **TimeoutPolicy**: Asynchronous systems depend heavily on timeouts and fixed timeouts are never right. TimeoutPolicy allows users to set policy (like 2.5 sigma) instead of fixed timeout values and takes care of the heuristics by itself allowing systems to adapt to their operating conditions.

7. **ActorRegistry**: A core lookup facility allowing actors of loosely-coupled modules to find each others, or even to model different services as actors.

8. **ActorMonitor**: An add-on operational module that uses JMX to report the stats and behavior of actors in the system. These stats can be seen by any JMX tooling

9. **Pipeline**: An infrastructure allowing sequencing and plugging in of request/response filters. These are used, for instance, for security, rate limiting, logging, etc.
Each of the components have virtually no dependency on each others. They are truly loosely coupled. Developers and organizations are free to pick and choose the components needed for their environment.

##Getting Started

The easiest way to getting started is to create a project from one of our templates. The followings are currently available templates:

TODO: Reference to template projects

##Documentation
* [Unicomplex & Cube Bootstrapping](docs/bootstrap.md)
* [Unicomplex Actor Hierarchy](docs/actor-hierarchy.md)
* [Runtime Lifecycle & API](docs/lifecycle.md)
* [Configuration Reference](docs/configuration.md)
* [Testing squbs Applications](docs/testing.md)
* [Clustering squbs Services using ZooKeeper](docs/zkcluster.md)
* [The Blocking Dispatcher for Blocking API calls](docs/blocking-dispatcher.md)
* [Message Guidelines](docs/messages.md)
* [Request/Response Pipeline Proxy](docs/pipeline.md)
* [Monitoring Actors at Runtime](docs/monitor.md)
* [Accessing Other Services using HTTP or HTTPS](docs/httpclient.md)
* [Using the Orchestration DSL](docs/orchestration_dsl.md)
* [The ActorRegistry](docs/registry.md)
* [Timeout Policy](docs/timeoutpolicy.md)

##Contributing to squbs
Thank you very much for contributing to squbs. Please read the [contribution guidelines](CONTRIBUTING.md) for the process.

##License
squbs is licensed under the [Apache License, v2.0](LICENSE.txt)
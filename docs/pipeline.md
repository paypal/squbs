#Request/Response Pipeline Proxy

### Overview
We often need to have common infrastructure functionality across different squbs
services, or as organizational standards. Such infrastructure includes, but is not
limited to logging, request tracing, authentication/authorization, tracking,
cookie management, A/B testing, etc.

As squbs promotes separation of concerns, such logic would belong to infrastructure
and not service implementation. The squbs proxy allows infrastructure to provide
components installed into a service without service owner having to worry about such
aspects in their service or actors.

Generally speaking, a squbs proxy is an actor acting as a bridge in between the
service (Spray) responder and the squbs service. That is to say:

* All request messages sent from resonder to squbs service will go thru the proxy actor
* Vice versa, all response messages sent from squbs service to responder will go thru the proxy actor.


### Proxy Binding

In your `squbs-meta.conf`, you can specify proxy for your service as follows:

```
squbs-services = [
  {
    class-name = com.myorg.myapp.MyActor
    web-context = mypath
    proxy-name = myProxy
  }
]
```

Please check out [Unicomplex & Cube Bootstrapping](bootstrap.md) for more
information about `squbs-meta.conf`.

**Proxy resolution rules**:

* If you don't specify any proxy-name, i.e.: omit the proxy-name, squbs will install
a proxy named "default-proxy" provided with your system configuration in some `reference.conf` or `application.conf`.
* If you set an empty string `""` for proxy-name, no proxy is applied to the
requests/responses for your service.
* For any other name, squbs will try to load proxy the proxy definition and configuration from the system configuration provided through the merged `reference.conf` and `application.conf` files.


### Proxy Definition & Configuration

Following is the proxy definition required in the conf files:


```
myProxy {
  type = squbs.proxy
  processorFactory = org.myorg.myapp.SomeProcessorFactory
  settings = {
    // Proxy-specific configuration/settings here.  
  }
}

```

Explaining the fields:

* `myProxy` is the name of the proxy which aligns with the binding in `squbs-meta.conf`.
* `type` must be `squbs.proxy` to be recognized as a proxy declaration.
* `processorFactory` is factory impl which can be used to create a proxy processor (`Processor` is discussed in a later section of this document).

   ```scala
   trait ProcessorFactory {
     def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor]
   }
   ```
* `settings` is an optional config object which can be used by processorFactory (as you can tell from the above create method).


### Proxy Processor

Proxy Processor is used to describe the behavior of the proxy, like how to handle HttpRequest, HttpResponse, chunked request/chunked response, etc.  It provides method hooks that allow implementers to define their own logic.

You can check the [Processor definition](../squbs-pipeline/src/main/scala/org/squbs/pipeline/Processor.scala).

This Processor will be used along with the proxy actor (in this case it is `PipelineProcessorActor`) to perform the proxy behavior:

![Processor](./img/Processor.jpg)

Implementation of this processor is optional. In most cases you don't have to create an implementation of your own. squbs already provides a very lightweight pipeline based processor for you. This is described in the sections below.


### RequestContext

As you might see from Processor definition, a class called `RequestContext` is widely used.
This class is an **immutable** data container which hosts all useful information across the request/response lifecycle.

Below is a basic structure of the `RequestContext` and corresponding response wrapper class information:

![RequestContext](./img/RequestContext.jpg)


#Pipeline Proxy

Touched upon above, squbs has a default simple pipeline processor implementation.

With this implementation, you can simply setup a proxy by:

#### 1. Implementing handlers

Handler definition:

```scala
trait Handler {
  def process(reqCtx: RequestContext)(implicit context: ActorRefFactory): Future[RequestContext]
}
```

HandlerFactory definition:

```scala
trait HandlerFactory {
  def create(config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Handler]
}
```

You need to implement a HandlerFactory to create your handler instances.

#### 2. Configuring the pipeline

Once you implemented the handler factory, it needs to be added to the configuration.

```
myProxy {

  type = squbs.proxy

  processorFactory = org.squbs.proxy.SimpleProcessorFactory

  settings = {
    inbound = [handler1, handler2]
    outbound = [handler3, handler4]
  }

}

handler1 {
	type = pipeline.handler
	factory = com.myorg.myhandler1
}

handler2 {
	type = pipeline.handler
	factory = com.myorg.myhandler2
}

handler3 {
	type = pipeline.handler
	factory = com.myorg.myhandler3
}

handler4 {
	type = pipeline.handler
	factory = com.myorg.myhandler4
}
```

Or you might check [sample config with description](../squbs-unicomplex/src/main/resources/reference.conf#L23)

For the above configuration:

* `processorFactory` must be `org.squbs.proxy.SimpleProcessorFactory`.
* `settings.inbound` is the sequence of request handlers.
* `settings.outbound` is the sequence of response handlers.
* `handlerX.type` must be `pipeline.handler`.
* `handlerX.factory` is the name of a class that implements HandlerFactory

Again, if you want to have custom logic for other phases like preInbound or postOutbound, you can extend [SimpleProcessor](../squbs-unicomplex/src/main/scala/org/squbs/proxy/SimpleProcessor.scala#L30) and create your own factory just like [SimpleProcessorFactory](../squbs-unicomplex/src/main/scala/org/squbs/proxy/SimpleProcessor.scala#L46)

####  Default proxy

Squbs has a [default proxy](../squbs-unicomplex/src/main/resources/reference.conf#L23) defined in squbs-unicomplex

So in your application, you can simply define your proxy config like this:

```
default-proxy {
  settings = {
    inbound = [handler1, handler2]
    outbound = [handler3, handler4]
  }
}

handler1 {
	type = pipeline.handler
	factory = com.myorg.myhandler1
}

handler2 {
	type = pipeline.handler
	factory = com.myorg.myhandler2
}

handler3 {
	type = pipeline.handler
	factory = com.myorg.myhandler3
}

handler4 {
	type = pipeline.handler
	factory = com.myorg.myhandler4
}

```

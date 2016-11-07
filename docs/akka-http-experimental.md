#Experimental Support for Akka HTTP

At this point, Akka HTTP support is still experimental. It will ultimately replace Spray as the HTTP engine for squbs as soon as performance, stability, and feature parity is achieved.

##Enabling Akka HTTP Experimental

To enable experimental Akka HTTP support, set the following configuration in `application.conf`: `squbs.experimental-mode-on = true`

##Programmatic Differences

For Akka HTTP, you need to declare your `route` using a different `RouteDefinition` trait than a Spray `route`. Change your import statement for `RouteDefinition` as follows:

From: `import org.squbs.unicomplex.RouteDefinition`

To: `import org.squbs.unicomplex.streaming.RouteDefinition`

Next, declare your route using Akka HTTP directives. If you're migrating from Spray, you can often just replace your imports as follows:

From: `import spray.routing.Directives._`

To: `import akka.http.scaladsl.server.Directives._`

But there are differences. Please see the [documentation for Akka HTTP directives](http://doc.akka.io/docs/akka/current/scala/http/routing-dsl/directives/index.html) for detail.

##Modules

These modules replace previous modules when using Akka HTTP. You need to ensure these are added to your `libraryDependencies` in your `build.sbt` instead of the default versions.

1. **StreamingPipeline**: Akka streams version of the Pipeline, based upon Akka Streams' `BidiFlow`. This artifact `squbs-streamingpipeline` replaces `squbs-pipeline` for Akka HTTP. It allows users or infrastructure teams to implement request/response filters as `BidiFlow` elements and registering them to the pipeline. Please consult [Streaming Pipeline documentation](streamingpipeline.md) for detail.

2. **Console for Akka HTTP**: The `squbs-admin-exp` module replaces `squbs-admin` in Akka HTTP experimental mode. It uses the Akka HTTP interfaces instead of the Spray interfaces. In experimental mode, users have to modify their build.sbt and use this dependency instead of squbs-admin.

#Testing squbs Applications
All tests on squbs have been written using ScalaTest 2.x. Specs2 has not yet been tested.

##Dependencies

To use test utilities mentioned in this documentation,, simply add the following dependencies in your `build.sbt` file or Scala build script:

```
"org.squbs" %% "squbs-testkit" % squbsVersion
```

##CustomTestKit

The `CustomTestKit` is used for starting a full blown squbs instance needed for testing bits and pieces of applications.  `CustomTestKit` is simple to use and needs no configuration by default, yet allows customizations and flexibility for your tests.  With `CustomTestKit` you can start any number of `ActorSystem` and `Unicomplex` instances (one per `ActorSystem`) with different configurations - all on the same JVM.  Here are some features:

   * It automatically sets actor system name to spec/test class name for simplicity and also to ensure `ActorSystem` instances do not conflict.  But, it also allows actor system name to be passed in the constructor.
   * Tests extending `CustomTestKit` can run in parallel in the same JVM.
   * Starts and stops squbs automatically.
   * Starts well-known actors and service in `src/test/resources/META-INF/squbs-meta.conf` by default.  But, allows passing `resources` to be scanned in the constructor.
   * Allows custom configuration to be passed in the constructor.

Here is an example usage of `CustomTestKit`:

```scala
class SampleSpec extends CustomTestKit with FlatSpecLike with Matchers {
   it should "start the system" in {
      system.startTime should be > 0L
   }
}
``` 

###Passing configuration to `CustomTestKit`

If you would like to customize the actor system configuration, you can pass a `Config` object to `CustomTestKit`:

```scala
object SampleSpec {
  val config = ConfigFactory.parseString {
      """
        |akka {
        |  loglevel = "DEBUG"
        |}
      """.stripMargin
  }
}

class SampleSpec extends CustomTestKit(SampleSpec.config) with FlatSpecLike with Matchers {

  it should "set akka log level to the value defined in config" in {
    system.settings.config.getString("akka.loglevel") shouldEqual "DEBUG"
  }
}
```
###Starting well-known actors and services with `CustomTestKit`

`CustomTestKit` will automatically start well-known actors and services in `src/test/resources/META-INF/squbs-meta.conf` (see [Bootstrapping squbs](bootstrap.md#well-known-actors)).  However, if you would like provide different resource paths, you can do so by passing a `Seq` of resources to the constructor.  `withClassPath` controls whether to scan entire test classpath for `META-INF/squbs-meta.conf` files or not.

```scala
object SampleSpec {
	val resources = Seq(getClass.getClassLoader.getResource("").getPath + "/SampleSpec/META-INF/squbs-meta.conf")
}

class SampleSpec extends CustomTestKit(SampleSpec.resources, withClassPath = false)
  with FlatSpecLike with Matchers {
	
  // Write tests here	
}
```

Please note, `CustomTestKit` allows passing `config` and `resources` together as well.

####Port binding in tests

Starting services requires port binding.  To prevent port conflicts, we should let the system pick a port by setting listeners' `bind-port` to 0, e.g., `default-listener.bind-port = 0` (this is what `CustomTestKit` sets by default).  `squbs-testkit` comes with a `trait` named `PortGetter` that allows retrieving the port picked by the system.  `CustomTestKit` comes with `PortGetter` already mixed in, so you can use `port` value.  

```scala
class SampleSpec extends CustomTestKit(SampleSpec.resources)
  
  "PortGetter" should "retrieve the port" in {
    port should be > 0
  }
}

```

By default, `PortGetter` returns `default-listener`'s port, which is the most common one.  If you need to retrieve another listener's bind port, you can either override `listener` method or pass listener name to `port` method:

```scala
class SampleSpec extends CustomTestKit(SampleSpec.resources)

  override def listener = "my-listener"

  "PortGetter" should "return the specified listener's port" in {
    port should not equal port("default-listener")
    port shouldEqual port("my-listener")
  }
}
```

###Manual `UnicomplexBoot` initialization

`CustomTestKit` allows a `UnicomplexBoot` instance to be passed as well.  This allows full customization of the bootrap.  Please see [Bootstrapping squbs](bootstrap.md) for more information on booting the Unicomplex.

```scala
object SampleSpec {
  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = SampleSpec # should be unique to prevent collision with other tests running in parallel
       |  ${JMX.prefixConfig} = true # to prevent JMX name collision, if you are doing any JMX testing
       |}
    """.stripMargin
  )

  val resource = getClass.getClassLoader.getResource("").getPath + "/SampleSpec/META-INF/squbs-meta.conf"	
	
  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanResources(resource)
    .initExtensions.start()
}

class SampleSpec extends CustomTestKit(SampleSpec.boot) with FunSpecLike with Matchers {

  // Write your tests here.
}
```

##Testing Spray/Akka Http Routes using Spray/Akka Http TestKit

The `spray-testkit` or `akka-http-testkit` needs to be added to the dependencies in order to test routes.  Please add the followings to your dependencies:

For Spray:

```
"io.spray" %% "spray-testkit" % sprayV % "test"
```

For Akka-Http:

```
"com.typesafe.akka" %% "akka-http-testkit" % akkaV % "test"
```

### Usage
As you specify routes in squbs by extending the `RouteDefinition` trait which squbs will compose with actors behind
the scenes, it can be difficult to construct routes for use with the Spray/Akka Http TestKit test DSL. `TestRoute` is provided
for constructing and obtaining routes from the `RouteDefinition`. To use it, just pass the `RouteDefinition` as a type
parameter to `TestRoute`. This will obtain you a fully configured and functional route for the test DSL as can be seen
in the example below.

Specifying the `RouteDefinition`

```scala
package com.myorg.mypkg

import org.squbs.unicomplex.RouteDefinition
import spray.routing.Directives._

class MyRoute extends RouteDefinition {

  val route =
    path("ping") {
      get {
        complete {
          "pong"
        }
      }
    }
}
```

Implementing the test, obtaining route from `TestRoute[MyRoute]`:

```scala
package com.myorg.mypkg

import org.scalatest.{Matchers, FlatSpecLike}
import org.squbs.testkit.TestRoute
import spray.testkit.ScalatestRouteTest

class MyRouteTest extends FlatSpecLike with Matchers with ScalatestRouteTest {

  val route = TestRoute[MyRoute]

  it should "return pong on a ping" in {
    Get("/ping") ~> route ~> check {
      responseAs[String] should be ("pong")
    }
  }
}
```

Alternatively, you may also want to pass a web context to your route. This can be done by passing it to `TestRoute` as follows:

```scala
  val route = TestRoute[MyRoute](webContext = "mycontext")
```

or just pass `"mycontext"` without the parameter name. The `TestRoute` signature without parameters is equivalent to passing the root context `""`.

###Using TestRoute with CustomTestKit

A need may arise to bootstrap `Unicomplex` while testing with `TestRoute`, such as when:

   * a squbs well-known actor is involved in request handling.
   * [The Actor Registry](registry) is used during request handling.

Using Akka's `TestKit` together with `ScalatestRouteTest` can be tricky as they have conflicting initialization.  squbs provides a test utility named `CustomRouteTestKit` to solve this problem.  `CustomRouteTestKit` supports all the APIs provided by `CustomTestKit`.  Here is an example usage of `TestRoute` with `CustomRouteTestKit`:  

```scala
class MyRouteTest extends CustomRouteTestKit with FlatSpecLike with Matchers {

  it should "return response from well-known actor" in {
    val route = TestRoute[ReverserRoute]
    Get("/msg/hello") ~> route ~> check {
      responseAs[String] should be ("hello".reverse)
    }
  }
}

class ReverserRoute extends RouteDefinition {
  import akka.pattern.ask
  import Timeouts._
  import context.dispatcher

  val route =
    path("msg" / Segment) { msg =>
      get {
        onComplete((context.actorSelection("/user/mycube/reverser") ? msg).mapTo[String]) {
          case Success(value) => complete(value)
          case Failure(ex)    => complete(s"An error occurred: ${ex.getMessage}")
        }
      }
    }
}
```

**Note:** To use `CustomRouteTestKit`, please ensure the Spray or Akka Http testkit is in your dependencies as described [above](#testing-sprayakka-http-routes-using-sprayakka-http-testkit).
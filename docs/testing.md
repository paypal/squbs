#Testing squbs Applications
All tests on squbs have been written using ScalaTest 2.x. Specs2 has not yet been tested.

Depending on the test requirements, squbs provides two traits to help write test cases:

1. `org.squbs.testkit.SimpleTestKit` The SimpleTestKit is used for starting a full blown squbs instance needed for testing bits and pieces of applications in a single Unicomplex instance and single ActorSystem. SimpleTestKit is simple to use and needs minimal configuration. It is however not suited for parallel tests that need different squbs configurations for each test case. Also, tests based on SimpleTestKit needs to be forked as a separate JVM. You have to make sure to set `fork in (Test,run) := true` in sbt. Please see the [sbt documentation](http://www.scala-sbt.org/0.13/docs/Forking.html) for more detail on forking.

2. `org.squbs.testkit.CustomTestKit` For those who need ultimate power and flexibility to control your tests and be able to run different test cases needing different configurations together and in parallel, CustomTestKit is the right answer. You can start any number of ActorSystems and Unicomplex instances, one per ActorSystem, with different configurations - all on the same JVM. You just have to ensure the ActorSystem and Unicomplex instances do not conflict.

The examples below show how each of the TestKit traits can be used.

##SimpleTestKit examples:
You can use SimpleTestKit by just extending the test case from it. squbs is going to be started and stopped automatically. Tests can run in parallel but only one squbs instance is allowed inside a forked VM.

```scala
package com.myorg.mypkg
import org.scalatest.{FunSpecLike, Matchers}
import org.squbs.testkit.SimpleTestKit

class ActorCalLogTest extends SqubsTestKit with FunSpecLike with Matchers {

  // Write your tests here.

}
```

##CustomTestKit example:

CustomTestKit is a more elaborate example allowing/requiring the developer to provide a configuration. Then use that configuration to boot the Unicomplex. Please see [Bootstrapping squbs](bootstrap.md) for more information on booting the Unicomplex.

```scala
package com.myorg.mypkg

import akka.actor.{ActorRef, Props}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpecLike, Matchers}
import org.squbs.testkit.CustomTestKit
import org.squbs.unicomplex.{UnicomplexBoot, JMX}

// First, setup an object that configures and creates the
// UnicomplexBoot (boot, in short) for this test.

object MyTest {

  import collection.JavaConversions._

  val configDir = "actorCalLogTestConfig"

  // Many forms to provide the configuration. A map is one simple way.
  // 1) You want to make sure the name of the ActorSystem does not collide
  // with other tests. Using the test class name is a sensible choice.
  // 2) If multiple Unicomplexes are running in the same JVM, make sure
  // to force prefixing the JMX bean registration or JMX names will collide.
  // To do so, set the config entry "squbs." + JMX.prefixConfig to true.
  
  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = myTest
       |  external-config-dir = $configDir
       |  ${JMX.prefixConfig} = true
       |}
    """.stripMargin
  )

  // Now, start the boot with the given config.
  val boot = UnicomplexBoot(config)
    .scanComponents(System.getProperty("java.class.path").split(File.pathSeparator))
    .initExtensions
    .start()
}

class MyTest extends CustomTestKit(MyTest.boot)
      with FunSpecLike with Matchers {
  
  // Write your tests here.      
}

```

##Testing Spray Routes using Spray TestKit

As you specify routes in squbs by extending the `RouteDefinition` trait which squbs will compose with actors behind
the scenes, it can be difficult to construct routes for use with the Spray TestKit test DSL. `TestRoute` is provided
for constructing and obtaining routes from the RouteDefinition. To use it, just pass the `RouteDefinition` as a type
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
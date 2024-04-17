# Testing squbs Applications
squbs supports ScalaTest 3.X for Scala, and TestNG & JUnit for Java test frameworks.

## Dependencies

To use test utilities mentioned in this documentation, simply add the following dependencies in your `build.sbt` file or Scala build script:

```scala
"org.squbs" %% "squbs-testkit" % squbsVersion
```

Optionally, you should also include the following dependencies based upon whether they are needed in your tests:

```scala
// Testing RouteDefinition...
"org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % "test",

// Using JUnit...
"junit" % "junit" % junitV % "test",
"com.novocode" % "junit-interface" % junitInterfaceV % "test->default",

// Using TestNG
"org.testng" % "testng" % testngV % Test,
```

## CustomTestKit

The `CustomTestKit` is used for starting a full blown squbs instance needed for testing bits and pieces of applications.  `CustomTestKit` is simple to use and needs no configuration by default, yet allows customizations and flexibility for your tests.  With `CustomTestKit` you can start any number of `ActorSystem` and `Unicomplex` instances (one per `ActorSystem`) with different configurations - all on the same JVM.  Here are some features:

   * It automatically sets actor system name to spec/test class name for simplicity and also to ensure `ActorSystem` instances do not conflict.  But, it also allows actor system name to be passed in the constructor.
   * Tests extending `CustomTestKit` can run in parallel in the same JVM.
   * Starts and stops squbs automatically.
   * Starts well-known actors and service in `src/main/resources/META-INF/squbs-meta.conf` and `src/test/resources/META-INF/squbs-meta.conf` by default.  But, allows passing `resources` to be scanned in the constructor.
   * Allows custom configuration to be passed in the constructor.

Here is an example usage of `CustomTestKit` in ScalaTest:

```scala
import org.squbs.testkit.CustomTestKit

class SampleSpec extends CustomTestKit with FlatSpecLike with Matchers {
   it should "start the system" in {
      system.startTime should be > 0L
   }
}
``` 

Both TestNG and JUnit are supported for Java users:

```java
import org.squbs.testkit.japi.CustomTestKit

public class SampleTest extends CustomTestKit {

    @Test
    public void testSystemStartTime() {
        Assert.assertTrue(system().startTime() > 0L);
    }
}
```

### Passing configuration to `CustomTestKit`

If you would like to customize the actor system configuration, you can pass a `Config` object to `CustomTestKit`:

```scala
object SampleSpec {
  val config = ConfigFactory.parseString {
      """
        |pekko {
        |  loglevel = "DEBUG"
        |}
      """.stripMargin
  }
}

class SampleSpec extends CustomTestKit(SampleSpec.config) with FlatSpecLike with Matchers {

  it should "set pekko log level to the value defined in config" in {
    system.settings.config.getString("pekko.loglevel") shouldEqual "DEBUG"
  }
}
```

Here is the TestNG/JUnit version of this test:

```java
import org.squbs.testkit.japi.CustomTestKit;

public class SampleTest extends CustomTestKit {

    public SampleTest() {
        super(TestConfig.config);
    }

    @Test
    public void testAkkaLogLevel() {
        Assert.assertEquals(system().settings().config().getString("pekko.loglevel"), "DEBUG");
    }

    private static class TestConfig {
        private static Config config = ConfigFactory.parseString("pekko.loglevel = DEBUG");
    }
}
```

The following sections show customizations only in ScalaTest; however, all these customizations are supported for TestNG and JUnit as well.  For customizations, provide a `public` constructor in your TestNG/JUnit tests and call `super` with custom parameters.  Check [squbs-testkit/src/test/java/org/squbs/testkit/japi](https://github.com/paypal/squbs/tree/master/squbs-testkit/src/test/java/org/squbs/testkit/japi) for more TestNG and JUnit samples.

Specifically for JUnit, avoid setting actor system name in your tests (letting `CustomTestKit` set the actor system name is in general a good practice though).  JUnit creates a new fixture instance for each `@Test` method which potentially causes actor system conflicts.  `AbstractCustomTestKit` avoids this by appending an incremented integer to each actor system name.

### Starting well-known actors and services with `CustomTestKit`

`CustomTestKit` will automatically start well-known actors and services in `src/test/resources/META-INF/squbs-meta.conf` (see [Bootstrapping squbs](bootstrap.md#well-known-actors)).  However, if you would like provide different resource paths, you can do so by passing a `Seq` *(Scala)* or `List` *(Java)* of resources to the constructor.  `withClassPath` controls whether to scan entire test classpath for `META-INF/squbs-meta.conf` files or not.

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

#### Port binding in tests

Starting services requires port binding.  To prevent port conflicts, we should let the system pick a port by setting listeners' `bind-port` to 0, e.g., `default-listener.bind-port = 0` (`CustomTestKit`, if used with default configuration, sets `bind-port = 0` for all listeners).  `squbs-testkit` comes with a `trait` named `PortGetter` that allows retrieving the port picked by the system.  `CustomTestKit` comes with `PortGetter` already mixed in, so you can use `port` value.  

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

### Manual `UnicomplexBoot` initialization

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

### Shutting Down

For large tests with many instances of `CustomTestKit` running in parallel, it is important to shutdown properly after the test. The shutdown mechanism depends on the test framework and how `CustomTestKit` is constructed, as follows:

#### ScalaTest

The `CustomTestKit` shuts down automatically, unless you override the `afterAll()` method. No further actions are needed.

#### TestNG

Use the `@AfterClass` annotation to annotate a method calling `shutdown()` as follows:

```java
    @AfterClass
    public void tearDown() {
        shutdown();
    }
```

#### JUnit

JUnit creates an instance of the class for every individual test in the class. This also means a new instance of the `Unicomplex` is created for each test method. This can be resource intensive. To ensure proper shutdown of the individual instances, use the `@After` JUnit annotation to annotate a method calling `shutdown()` as follows:

```java
    @After
    public void tearDown() {
        shutdown();
    }
```

Note: If you construct the `CustomTestKit` passing a `UnicomplexBoot` object on the `super(boot)` call, use caution how and when to shutdown. If the `UnicomplexBoot` instance is created per-class, meaning one single instance is used for all test methods, the shutdown also needs to happen only once. Use JUnit's `@AfterClass` annotation to annotate a static shutdown method. But if the `UnicomplexBoot` instance is created per test method - the default behavior, the `@After` annotation should be used similar to default construction of `CustomTestKit`.

## Testing Pekko Http Routes using Pekko Http TestKit

The `pekko-http-testkit` needs to be added to the dependencies in order to test routes.  Please add the followings to your dependencies:

```
"org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV % "test"
```

### Usage

The squbs testkit provides utilities to construct routes from the `RouteDefinition` trait *(Scala)* or the `AbstractRouteDefinition` class *(Java)* and has utilities for both standalone and full system mode  where the infrastructure and cubes can be brought up as part of the test.

##### Scala

`TestRoute` is provided
for constructing and obtaining routes from the `RouteDefinition`. To use it, just pass the `RouteDefinition` as a type
parameter to `TestRoute`. This will obtain you a fully configured and functional route for the test DSL as can be seen
in the example below.

Specifying the `RouteDefinition`

```scala
import org.squbs.unicomplex.RouteDefinition

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
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, FlatSpecLike}
import org.squbs.testkit.TestRoute

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

##### Java

In order to test the route, the test needs to extend the proper `RouteTest` abstract class for the test framework of your choice. Choose to extend `org.squbs.testkit.japi.TestNGRouteTest` if you are using TestNG, or `org.squbs.testkit.japi.JUnitRouteTest` if you're using JUnit. The usage for both are the same.

Once the test extends one of the `RouteTest` classes you can construct and obtain a `TestRoute` from the `AbstractRouteDefinition` by passing the class to `testRoute(MyRoute.class)` call as follows:

```java
TestRoute myRoute = testRoute(MyRouteDefinition.class);
```

This will obtain you a fully configured and functional route for the test DSL as can be seen
in the example below.

Specifying the `RouteDefinition`

```java
import org.squbs.unicomplex.AbstractRouteDefinition;

class MyRoute extends AbstractRouteDefinition {

    @Override
    public Route route() throws Exception {
        return path("ping", () ->
                get(() ->
                        complete("pong")
                )
        );
    }
}
```

Implementing the test, obtaining route from `testRoute(MyRoute.class)`:

```java
import org.squbs.testkit.japi.JUnitRouteTest;

public class MyRouteTest extends JUnitRouteTest {

    @Test
    public void testPingPongRoute() {
        TestRoute myRoute = testRoute(MyRoute.class);
        myRoute.run(HttpRequest.GET("/ping"))
                .assertStatusCode(200)
                .assertEntity("pong");
    }
}
```

Alternatively, you may also want to pass a web context to your route. This can be done by passing it to `TestRoute` as follows:

```java
TestRoute myRoute = testRoute("mycontext", MyRoute.class);
```

The `TestRoute` signature without parameters is equivalent to passing the root context `""`.

### Using TestRoute with CustomTestKit

A need may arise to bootstrap `Unicomplex` while testing with `TestRoute`, such as when:

   * a squbs well-known actor is involved in request handling.
   * [The Actor Registry](registry.md) is used during request handling.

Using Pekko's `TestKit` together with `ScalatestRouteTest` can be tricky as they have conflicting initialization.  squbs provides test utilities named `CustomRouteTestKit` *(Scala)*, `TestNGCustomRouteTestKit`, and `JUnitCustomRouteTestKit` *(Java, for each test framework)* to solve this problem.  `CustomRouteTestKit` supports all the APIs provided by `CustomTestKit`.  Here are example usages of `TestRoute` with `CustomRouteTestKit`:  

##### Scala

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
  import org.apache.pekko.pattern.ask
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

##### Java

```java
public class MyRouteTest extends TestNGCustomRouteTestKit {

    @Test
    public void testHelloReverse() {
        TestRoute route = testRoute(ReverserRoute.class);
        route.run(HttpRequest.GET("/msg/hello"))
                .assertStatusCode(200)
                .assertEntity("olleh");
    }
}
```

And the corresponding route to test would be as follows:

```java
import org.apache.pekko.http.javadsl.server.Route;
import org.squbs.unicomplex.AbstractRouteDefinition;

public class ReverserRoute extends AbstractRouteDefinition {

    @Override
    public Route route() throws Exception {
        return path(segment("msg").slash(segment()), msg ->
                onSuccess(() ->
                        ask(context().actorSelection("/user/mycube/reverser"), msg, 5000L), response ->
                                complete(response.toString())
                        )
                )
        );
    }
}

```

**Note:** To use `CustomRouteTestKit`, please ensure Pekko Http testkit is in your dependencies as described [above](#testing-pekko-http-routes-using-pekko-http-testkit).

#### Shutting Down

The `CustomRouteTestKit` abstract classes and traits are specific to the test framework and therefore they have been pre-instrumented with test framework hooks to start and to shutdown properly. Hence there is no need to take care of shutting down the `Unicomplex` in test cases using any flavor of `CustomRouteTestKit`.

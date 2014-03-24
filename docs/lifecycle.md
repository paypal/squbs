Bootstrapping squbs
===================

squbs divides applications into modules called cubes. Modules in squbs are intended to be run in modular isolation as
well as on a flat classpath. Modular isolation is intended for true loose coupling of the modules, without incurring
any classpath conflicts due to the dependencies.

The current implementation bootstraps from a flat classpath. Modular isolation is planned for a future version of squbs.

Cube Jars
---------

All cubes are represented by a top-level jar file with the cube logic itself. All cubes must have the cube metadata
residing in META-INF/squbs-meta.&lt;ext&gt;. Supported extensions are .conf, .json, and .properties. The format follows the
[Typesafe config](https://github.com/typesafehub/config) format.

At the minimum, the cube metadata uniquely identifies the cube and version and declares and configures one or more of
the followings:

*Actor*: Identifies the well known actors automatically started by squbs.

*Service*: Identifies a squbs service.

*Extension*: Identifies a squbs framework extension. The extension entry point has to extend from
    org.squbs.lifecycle.ExtensionInit trait.


Well Known Actors
-----------------

Well known actors are just [Akka actors](http://doc.akka.io/docs/akka/2.2.3/scala/actors.html) as defined by the
[Akka documentation](http://doc.akka.io/docs/akka/2.2.3/scala/actors.html). They are started by a supervisor actor that
is created for each cube. The supervisor carries the name of the cube. Therefore any well known actor has a path of
/&lt;CubeName&gt;/&lt;ActorName&gt; and can be looked up using the ActorSelection call under /user/&lt;CubeName&gt;/&lt;ActorName&gt;.

A well known actor can be started as a singleton actor or with a router. To declare a well known actor as a router,
add:
    with-router = true
in the actor declaration. Router, dispatcher, and mailbox configuration for well known actors are done in
reference.conf or application.conf following the Akka documentation.

Following is a sample cube declaration META-INF/squbs-meta.conf declaring a well known actor:

```
cube-name = org.squbs.bottlecube
cube-version = "0.0.2-SNAPSHOT"
squbs-actors = [
  {
    class-name = org.squbs.bottlecube.LyricsDispatcher
    name = lyrics
    with-router = false  # Optional, defaults to false
  }
]
```

If an actor is configured with-router (with-router = true) and a non-default dispatcher, the intention is usually to
schedule the actor (routee) on the non-default dispatcher. The router will assume the well known actor name, not the
routee (your actor implementation). A dispatcher set on the router will only affect the router, not the routee. To
affect the routee, you need to create a separate configuration for the routees (by appending "/*" to the name) and
configure the dispatcher in the routee section as the following example.

```
akka.actor.deployment {

  # Router configuration
  /bottlecube/lyrics {
    router = round-robin
    resizer {
      lower-bound = 1
      upper-bound = 10
    }
  }

  # Routee configuration. Since it has a '*', the name has to be quoted.
  "/bottlecube/lyrics/*" {
    # Configure the dispatcher on the routee.
    dispatcher = blocking-dispatcher
  }
```

Router concepts, examples, and configuration, are documented in the
[Akka documentation](http://doc.akka.io/docs/akka/2.2.3/scala/routing.html).

Services
--------

Services extend from org.squbs.unicomplex.RouteDefinition trait and have to provide 2 components.

1. The webContext - a String that uniquely identifies the web context of a request to be dispatched to this service.
   This webContext must be a lowercase alphanumeric string without any slash ('/') character
2. The route - A Spray route according to the
   [Spray documentation](http://spray.io/documentation/1.2.0/spray-routing/key-concepts/routes/).

```
cube-name = org.squbs.bottlesvc
cube-version = "0.0.2-SNAPSHOT"
squbs-services = [
  {
    class-name = org.squbs.bottlesvc.BottleSvc
  }
]
```


Extensions
----------

Extensions for squbs are low level facilities that need to be started for the environment. The extension initializer
has to extend from the org.squbs.lifecycle.ExtensionInit trait and override the proper callbacks. An extension
has great capabilities to introspect the system and provide additional functionality squbs by itself does not provide.
An extension must not be combined with an actor or a service in the same cube.

Extensions are started serially, one after another. Providers of extensions can provide a sequence for the extension by
specifying:
    sequence = [number]
in the extension declaration. If the sequence is not specified, it defaults to Int.maxValue. This means it will start
after all extensions that provide a sequence number. If there is more than one extension not specifying the sequence,
the order between them is indeterministic.

Bootstrapping
-------------

Bootstrapping squbs is done by starting the org.squbs.unicomplex.Bootstrap object from the Java command line, IDE, sbt,
or even maven. Bootstrap scans the classpath and finds META-INF/squbs-meta.&lt;ext&gt; in each classpath entry.
If squbs metadata is available, the jar is treated as squbs cube or extension and initialized according to the
metadata declarations. The bootstrap then first initializes extensions, cubes, then service routes last regardless of
their sequence in the classpath.

Shutting Down squbs
===================

The squbs runtime can be properly shutdown by sending the Unicomplex a GracefulStop message. 
Or `org.squbs.unicomplex.Shutdown` can be set as the main method in some monitor process like JSW for shutting down the squbs system. 

After receiving the `GracefulStop` message, the Unicomplex actor will stop the service and propagate the `GracefulStop`
message to all cube supervisors. Each supervisor will be responsible for stopping the actors in its cube 
(by propagating the `GracefulStop` message to its children who wants to perform a gracefull stop), 
ensure they stopped successfully or re-send a `PoisonPill` after timeout, and then stop itself. 
Once all cube supervisors and service are stopped, the squbs system shuts down. Then a shutdown hook will be
invoked to stop all the extensions and finally exits the JVM.

There is currently no standard console to a web container allowing users of squbs to build their own. The web console could
provide proper user shutdown by sending a stop message to Unicomplex as follows:

```
  Unicomplex() ! GracefulStop
```

squbs Runtime Lifecycle States
==============================

The runtime has the following lifecycle states:

* Starting - the initial state when squbs comes up.

* Initializing - Unicomplex started. Services starting. Cubes starting. Waiting for init reports.

* Active - Ready to do work and take service calls.

* Failed - Cubes did not start properly.

* Stopping - GracefulStop message received at Unicomplex. Terminating cubes, actors, and unbinding services.

* Stopped - squbs runtime stopped. Unicomplex terminated. ActorSystem terminated.

Lifecycle Hooks
===============

Most actors don't care when they are started or shut down. However, there may be a category of actors that require
execution certain initializations before they get to the state of accepting general traffic. Similarly, certain actors
also care about being notified before shutting down allowing them to properly clean up before sending them a poison
pill. Lifecycle hooks are here for this very reason.

You can make your actor register lifecycle events by sending `ObtainLifecycleEvents(states: LifecycleState*)` to `Unicomplex()`.
Then once the system state changed, your actor will receive the lifecycle state.

Startup Hooks
-------------

An actor wishing to participate in initialization must indicate so in the squbs metadata META-INF/squbs-meta.conf as
follows:

```
cube-name = org.squbs.bottlecube
cube-version = "0.0.2-SNAPSHOT"
squbs-actors = [
  {
    class-name = org.squbs.bottlecube.LyricsDispatcher
    name = lyrics
    with-router = false  # Optional, defaults to false
    init-required = true # Tells squbs we need to wait for this actor to signal they have fully started. Default: false
  }
```

Any actor with init-required set to true needs to send a Initialized(report) message to the cube supervisor, the parent
actor of these well known actors. The report being of type Try[Option[String]] allows the actor to report both
initialization success and failure with the proper exception. The squbs runtime is moves to the *Active* state once
all cubes are successfully initialized. This also means each actor with init-required set to true submitted an
Initialized(report) with success. If any one cube reports an initialization error via the Initialization(report), the
squbs runtime will be in *Failed* state instead.

Shutdown Hooks
--------------

### Stop Actors

We provide the trait `org.squbs.lifecycle.GracefulStopHelper` to let users achieve a graceful stop in their own actors' code.
You cam mix this trait in your actor in the following way.

```scala
class MyActor extends Actor with GracefulStopHelper {
    ...
}
```

The trait provides some helper methods to support graceful stop of an actor in Squbs framework.

`StopTimeout`
```scala
  /**
   * Duration that the actor needs to finish the graceful stop.
   * Override it for customized timeout and it will be registered to the reaper
   * Default to 5 seconds
   * @return Duration
   */
  def stopTimeout: FiniteDuration =
    FiniteDuration(config.getMilliseconds("default-stop-timeout"), TimeUnit.MILLISECONDS)
```
You can override the method to indicate how long approximately this actor needs to perform a graceful stop.
Once the actor is started, it will send the `stopTimeout` to its parent actor in `StopTimeout(stopTimeout)` message.
You can have the behavior in the parent actor to handle this message if you care about it.

If you mixed this trait in your actors' code, you should have a behavior in the `receive` method to handle the `GracefulStop`
message, because only in this case you can hook your code to perform a graceful stop
(You cannot add custom behavior towards `PoisonPill`).
In addition, you need to deal with the actor structure in your cube, since the cube supervisors will only propagate
the `GracefulStop` message to all its children who mixed this trait.

We also provides the following 2 default strategies in the trait.

```scala
  /**
   * Default gracefully stop behavior for leaf level actors
   * (Actors only receive the msg as input and send out a result)
   * towards the `GracefulStop` message
   *
   * Simply stop itself
   */
  protected final def defaultLeafActorStop: Unit = {
    log.debug(s"Stopping self")
    context stop self
  }
```

```scala
  /**
   * Default gracefully stop behavior for middle level actors
   * (Actors rely on the results of other actors to finish their tasks)
   * towards the `GracefulStop` message
   *
   * Simply propagate the `GracefulStop` message to all actors
   * that should be stop ahead of this actor
   *
   * If some actors failed to respond to the `GracefulStop` message,
   * It will send `PoisonPill` again
   *
   * After all the actors get terminated it stops itself
   */
  protected final def defaultMidActorStop(dependencies: Iterable[ActorRef],
                                          timeout: FiniteDuration = stopTimeout / 2): Unit = {

    def stopDependencies(msg: Any) = {
      Future.sequence(dependencies.map(gracefulStop(_, timeout, msg)))
    }

    stopDependencies(GracefulStop).onComplete({
      // all dependencies has been terminated successfully
      // stop self
      case Success(result) => log.debug(s"All dependencies was stopped. Stopping self")
        if (context != null) context stop self

      // some dependencies are not terminated in the timeout
      // send them PoisonPill again
      case Failure(e) => log.warning(s"Graceful stop failed with $e in $timeout")
        stopDependencies(PoisonPill).onComplete(_ => {
          // don't care at this time
          if (context != null) context stop self
        })
    })
  }
```

### Stop Extensions

By overriding the `def shutdown(jarConfig: Seq[(String, Config)]) {}` method in `org.squbs.lifecycle.ExtensionLifecycle`,
you can add your custom behavior to shut down an extension. Please notice that this method of all extensions will be executed
after the actor system gets terminated. If any extension throws exceptions during shutdown, JVM will exit with -1.

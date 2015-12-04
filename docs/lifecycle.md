
#Runtime Lifecycle & API

Lifecycle is really a concern of the infrastructure. Applications rarely have to touch on or even be aware of the system's lifecycle. System components, admin consoles, or even application components or actors that take a long time to initialize, and need to be fully initialized before the system can be made available for traffic will need to be aware of the system lifecycle. The latter includes functions such as cache controllers, cache loaders, device initializers, etc.

The squbs runtime exposes the following lifecycle states:

* **Starting** - the initial state when squbs comes up.

* **Initializing** - Unicomplex started. Services starting. Cubes starting. Waiting for init reports.

* **Active** - Ready to do work and take service calls.

* **Failed** - Cubes did not start properly.

* **Stopping** - GracefulStop message received at Unicomplex. Terminating cubes, actors, and unbinding services.

* **Stopped** - squbs runtime stopped. Unicomplex terminated. ActorSystem terminated.

##Lifecycle Hooks

Most actors don't care when they are started or shut down. However, there may be a category of actors that require
execution certain initializations before they get to the state of accepting general traffic. Similarly, certain actors
also care about being notified before shutting down allowing them to properly clean up before sending them a poison
pill. Lifecycle hooks are here for this very reason.

You can make your actor register lifecycle events by sending `ObtainLifecycleEvents(states: LifecycleState*)` to `Unicomplex()`.
Then once the system state changed, your actor will receive the lifecycle state.

You can also obtain the current state by sending `SystemState` to `Unicomplex()`. You'll get the response as one of the states above. All system state objects extend `org.squbs.unicomplex.LifecycleState` and are all part of the `org.squbs.unicomplex` package listed as followings:

* `case object Starting extends LifecycleState`
* `case object Initializing extends LifecycleState`
* `case object Active extends LifecycleState`
* `case object Failed extends LifecycleState`
* `case object Stopping extends LifecycleState`
* `case object Stopped extends LifecycleState`
 

##Startup Hooks

An actor wishing to participate in initialization must indicate so in the squbs metadata META-INF/squbs-meta.conf as
follows:

```
cube-name = org.squbs.bottlecube
cube-version = "0.0.2"
squbs-actors = [
  {
    class-name = org.squbs.bottlecube.LyricsDispatcher
    name = lyrics
    with-router = false  # Optional, defaults to false
    init-required = true # Tells squbs we need to wait for this actor to signal they have fully started. Default: false
  }
```

Any actor with init-required set to true needs to send a Initialized(report) message to the cube supervisor which is the parent
actor of these well known actors. The report being of type `Try[Option[String]]` allows the actor to report both
initialization success and failure with the proper exception. The squbs runtime is moved to the *Active* state once
all cubes are successfully initialized. This also means each actor with init-required set to true submitted an
Initialized(report) with success. If any one cube reports an initialization error via the Initialization(report), the
squbs runtime will end up in *Failed* state instead.

##Shutdown Hooks

### Stop Actors

The trait `org.squbs.lifecycle.GracefulStopHelper` lets users achieve graceful stop in their own actors' code.
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
(You cannot add custom behavior towards `PoisonPill`). Supervisors will only propagate the `GracefulStop` message to children that mixed in the `GracefulStopHelper` trait. The implementation of the children is expected to handle this message in their `receive` block.

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

### Stopping squbs Extensions

You can add custom behavior at extension shutdown by overriding the `shutdown()` method in `org.squbs.lifecycle.ExtensionLifecycle`. Note that this method in all installed extensions will be executed after termination of the actor system. If any extension throws exceptions during shutdown, JVM will exit with -1.

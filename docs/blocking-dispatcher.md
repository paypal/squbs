Blocking-Dispatcher
===================

This topic is not about dispatchers in general, but about squbs-specific dispatcher configurations. Please check the [Akka documentation](http://doc.akka.io/docs/akka/2.2.3/scala/dispatchers.html) for descriptions and details of dispatchers.

squbs adds another pre-configured dispatcher for use in blocking calls. Generally, these are used for synchronous calls to the database. The reference.conf defining the blocking-dispatcher is as follows:

```
blocking-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "fork-join-executor"
  # Configuration for the fork join pool
  fork-join-executor {
    # Min number of threads to cap factor-based parallelism number to
    parallelism-min = 2
    # Parallelism (threads) ... ceil(available processors * factor)
    parallelism-factor = 3.0
    # Max number of threads to cap factor-based parallelism number to
    parallelism-max = 24
  }
  # Throughput defines the maximum number of messages to be
  # processed per actor before the thread jumps to the next actor.
  # Set to 1 for as fair as possible.
  throughput = 2
}
```

For an actor to use the blocking dispatcher, just specify the actor configuration as in the following example:

```
  "/mycube/myactor" {
    dispatcher = blocking-dispatcher
  }
```

Without any actor using the blocking-dispatcher, it won't be initialized and will not require any resources.

**WARNING:** The blocking-dispatcher should only be used for blocking calls or performance could be severely impacted.
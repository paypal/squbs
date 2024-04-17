
# The Blocking Dispatcher for Blocking API Calls

This topic is not about dispatchers in general, but about squbs-specific dispatcher configurations. Please check the [Pekko documentation](http://doc.pekko.io/docs/pekko/2.3.13/scala/dispatchers.html) for descriptions and details of dispatchers.

squbs adds another pre-configured dispatcher for use in blocking calls. Generally, these are used for synchronous calls to the database. The reference.conf defines the blocking-dispatcher as follows:

```
blocking-dispatcher {
  # Dispatcher is the name of the event-based dispatcher
  type = Dispatcher
  # What kind of ExecutionService to use
  executor = "thread-pool-executor"
  thread-pool-executor {
    # Min number of threads to cap factor-based core number to
    core-pool-size-min = 2
    # The core pool size factor is used to determine thread pool core size
    # using the following formula: ceil(available processors * factor).
    # Resulting size is then bounded by the core-pool-size-min and
    # core-pool-size-max values.
    core-pool-size-factor = 3.0
    # Max number of threads to cap factor-based number to
    core-pool-size-max = 24
    # Minimum number of threads to cap factor-based max number to
    # (if using a bounded task queue)
    max-pool-size-min = 2
    # Max no of threads (if using a bounded task queue) is determined by
    # calculating: ceil(available processors * factor)
    max-pool-size-factor  = 3.0
    # Max number of threads to cap factor-based max number to
    # (if using a  bounded task queue)
    max-pool-size-max = 24
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

Without any actor using the blocking-dispatcher, the blocking-dispatcher won't be initialized and will not require any resources.

**WARNING:** The blocking-dispatcher should only be used for blocking calls or performance could be severely impacted.

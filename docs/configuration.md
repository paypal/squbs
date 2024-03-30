# Configuration Reference

The followings lists the squbs configuration as defined in `reference.conf`:

```
squbs {

  # Name of the actor system for squbs to create.
  actorsystem-name = "squbs"

  # graceful stop timeout
  # default timeout for one actor to process a graceful stop
  # if extends the trait org.squbs.lifecycle.GracefulStopHelper
  default-stop-timeout = 3s

  # An external configuration directory to supply external application.conf. The location of this directory
  # is relative to the working directory of the squbs process.
  external-config-dir = squbsconfig

  # An external configuration file name list. Any file with the name in the list under the external-confi-dir will be
  # loaded during squbs initialization for Actor System settings. Implicit "application.conf" will be loaded
  # besides this file name list
  external-config-files = []

  # Service infra configuration.
  service-infra {
    # Maximum amount of time to wait for all listeners to be started.
    timeout = 60s
    # Maximum amount of time each listener is given to start.
    listener-timeout = 10s
  }
}

default-listener {

  # All squbs listeners carry the type "squbs.listener"
  type = squbs.listener

  # Add aliases for the listener in case the cube's route declaration binds to a listener with a different name.
  # Just comma separated names are good, like...
  # aliases = [ foo-listener, bar-listener ]
  aliases = []

  # Service bind to particular address/interface. The default is 0.0.0.0 which is any address/interface.
  bind-address = "0.0.0.0"

  # Whether or not using full host name for address binding
  full-address = false

  # Service bind to particular port. 8080 is the default.
  bind-port = 8080

  # Listener uses HTTPS?
  secure = false

  # HTTPS needs client authorization? This configuration is not read if secure is false.
  need-client-auth = false

  # Any custom SSLContext provider? Setting to "default" means platform default.
  ssl-context = default
}

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

## Blocking Dispatcher

The squbs `reference.conf` declares a `blocking-dispatcher` used for blocking I/O calls. This is a standard pekko dispatcher configuration. Please see [dispatchers](http://doc.pekko.io/docs/pekko/2.3.13/scala/dispatchers.html) in the pekko documentation for more detail.

## Listeners

A listener defines a port binding and the behavior of this port binding such as security, authentication, etc. A default listener is provided by the squbs `reference.conf`. This can be overridden by the application providing its `application.conf` file or the `application.conf` file in its external config directory. Please see [Bootstrapping squbs](bootstrap.md#configuration-resolution) for details how squbs reads its configuration file.

A listener is declared at the root level of the configuration file. The name generally follows the pattern `*-listener` but this is not a requirement. What defines the entry as a listener is the `type` field under the listener entry. It must be set to `squbs.listener`. Please see the `default-listener` example above on how to configure new listeners listening to different ports.

A declared listener is not started unless a service route attaches itself to this listener. In other words, just declaring the listener does not automatically cause the listener to start unless there is a real use for the listener.

## Pipeline

If defined, a default pipeline is installed for pre-processing every single request and post-processing every response. Services can specify a different pipeline, or none at all as described under [Bootstrapping squbs](bootstrap.md#services). Applications or infrastructure can implement their own pipelines for pre-processing needs such as logging or tracing. Please see detailed description of pipelines under [Request/Response Pipeline](pipeline.md).

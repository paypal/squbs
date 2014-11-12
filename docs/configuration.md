#Configuration

The followings lists the squbs configuration as defined in `reference.conf`:

```
squbs {

  # Name of the actor system for squbs to create.
  actorsystem-name = "squbs"

  # If the application wants to take over Spray altogether, services should not be started by squbs.
  # This is true for applications that want to use the lower levels of Spray, such as spray-can.
  # Service sharing is not supported in this case. This is also set to false if Spray is not supposed to bind the port.
  // TODO: Consider removing this.
  start-service = true

  # graceful stop timeout
  # default timeout for one actor to process a graceful stop
  # if extends the trait org.squbs.lifecycle.GracefulStopHelper
  default-stop-timeout = 3s

  # An external configuration directory to supply external application.conf. The location of this directory
  # is relative to the working directory of the squbs process.
  external-config-dir = squbsconfig

  # An external configuration file name list. Any file with the name in the list under the external-confi-dir will bes
  # loaded during squbs initialization for Actor System settings. Implicit "application.conf" will be loaded
  # besides this file name list
  external-config-files = []
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

  # Binding the service with address & port, by default is true.
  # There is one use cases for Higgins, start the service, but not bind-service since Higgins will take care of binding.
  bind-service = true

  # Listener uses HTTPS?
  secure = false

  # HTTPS needs client authorization? This configuration is not read if secure is false.
  client-authn = false

  # SSLContext fully qualified classname. Setting to "default" means platform default.
  # ssl-context = org.foobar.MySSLContext
  ssl-context = default
}

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

##Blocking Dispatcher

The squbs `reference.conf` declares a `blocking-dispatcher` used for blocking I/O calls. This is a standard Akka dispatcher configuration. Please see [dispatchers](http://doc.akka.io/docs/akka/2.3.3/scala/dispatchers.html) in the Akka documentation for more detail.

##Listeners

A listener defines a port binding and the behavior of this port binding such as security, authentication, etc. A default listener is provided by the squbs `reference.conf`. This can be overridden by the application providing it's `application.conf` file or the `application.conf` file in its external config directory. Please see [Bootstrapping squbs](bootstrap.md#configuration-resolution) for details how squbs reads its configuration file.

A listener is declared at the root level of the configuiration file. The name generally follows the pattern *-listener but this is not a requirement. What defines the entry as a listener is the `type` field under the listener entry. It MUST be set to `squbs.listener`. Please see the default-listener example above on how to configure new listeners listening to different ports.

A declared listener is not started unless a service route attaches itself to this listener. In other words, just declaring the listener does not automatically cause the listener to start unless there is a real use for the listener.
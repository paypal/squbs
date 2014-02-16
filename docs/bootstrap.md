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

TODO: Sample service declaration

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
-------------------

At this point in time, we just terminate the process to shutdown squbs. This attribute of squbs will be improved over
time to provide a proper shutdown.

#Bootstrapping squbs
squbs is provided with a default bootstrap class developers can just use to call from the command line. The class to start is `org.squbs.unicomplex.Bootstrap`. Given normal circumstances, bootstrapping detail are irrelevant. However, one may need to programmatically bootstrap squbs in different ways. This is especially common in test cases needing custom configuration and needing to run in parallel. Please see [Testing](testing.md) for more information. The syntax for bootstrapping squbs is as follows:

**Option 1)** Start with user-defined configuration

```
UnicomplexBoot(customConfig)
  .createUsing {(name, config) => ActorSystem(name, config)}
  .scanComponents(System.getProperty("java.class.path").split(File.pathSeparator))
  .initExtensions
  .stopJVMOnExit
  .start()
```

**Option 2)** Start with default configuration

```
UnicomplexBoot {(name, config) => ActorSystem(name, config)}
  .scanComponents(System.getProperty("java.class.path").split(File.pathSeparator))
  .initExtensions
  .stopJVMOnExit
  .start()  
```

Lets take a look at each component.

1. Creating the UnicomplexBoot (boot) object. This can be done by passing a custom config or an actor system creator function to `UnicomplexBoot.apply()`.

2. The configuration object shown as customConfig in the example above. This is a configuration object obtained from the Typesafe Config library's parse functions. This config object is not yet merged with `reference.conf`. It is optional and substitutes other `application.conf` configurations defined.

3. The ActorSystem creator passes a function to create the ActorSystem. The actual creation happens in the start phase (item 7, below). The default function is `{(name, config) => ActorSystem(name, config)}`. The name passed in is the intended ActorSystem name read from the configuration. The config is the loaded configuration object after merging with any provided config. Most use cases would want to create the ActorSystem this way and thus the function need not be provided.

4. Scanning components looking for cubes, services, or extensions using the `scanComponents()` function. This is mandatory as there would be no components to start. Normally, the squbs bootstrap will scan the whole classpath. Test cases may want to target certain components to scan only.

5. Initialize the extensions using the `initExtentension` function. This will initialize the extensions scanned. Extension initialization is done before the ActorSystem is created. For multiple Unicomplex cases (multiple ActorSystems), the same extension must not be initialized more than once. An extension can only be used by one test case. In some cases we do not want to initialize the extensions at all and would not call `initExtension`

6. Stopping the JVM on exit. This is enabled by calling the `stopJVMOnExit` function. This option should generally not be used for test cases. It is used by squbs' bootstrap to make sure squbs shuts down and exits properly.

7. Starting the Unicomplex by calling `start()`. This is a mandatory step. Without it no ActorSystems start and no Actor would be able to run. It also runs other housekeeping tasks.


#Configuration Resolution

squbs chooses exactly one application configuration and merges it with the aggregated reference.conf. The application configuration being merged are chosen from the following order.

1. If a configuration is provided when creating the boot object, this configuration is chosen. This is the `customConfig` field from the example above.

2. If an `application.conf` file is provided in the external config directory, this `application.conf` is chosen. The external config dir is configured by setting the config property `squbs.external-config-dir` and defaults to `squbsconfig`. Not that this directory cannot be changed or overridden by supplied configuration or an external configuration (as the directory itself is determined using the config property.)

3. Otherwise, the `application.conf` provided with the application, if any, will be used. This then falls back to the reference.conf.

#Drop-in Modular System 

squbs divides applications into modules called cubes. Modules in squbs are intended to be run in modular isolation as
well as on a flat classpath. Modular isolation is intended for true loose coupling of the modules, without incurring
any classpath conflicts due to the dependencies.

The current implementation bootstraps from a flat classpath. Modular isolation is planned for a future version of squbs.

On bootstrapping, squbs will automatically detect the modules by classpath scanning. Scanned cubes are detected and started automatically.

##Cube Jars

All cubes are represented by a top-level jar file with the cube logic itself. All cubes must have the cube metadata
residing in META-INF/squbs-meta.&lt;ext&gt;. Supported extensions are .conf, .json, and .properties. The format follows the
[Typesafe config](https://github.com/typesafehub/config) format.

At the minimum, the cube metadata uniquely identifies the cube and version and declares and configures one or more of
the followings:

*Actor*: Identifies the well known actors automatically started by squbs.

*Service*: Identifies a squbs service.

*Extension*: Identifies a squbs framework extension. The extension entry point has to extend from
    org.squbs.lifecycle.ExtensionInit trait.


##Well Known Actors

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

##Services

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

##Extensions

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

##Bootstrapping

Bootstrapping squbs is done by starting the org.squbs.unicomplex.Bootstrap object from the Java command line, IDE, sbt,
or even maven. Bootstrap scans the classpath and finds META-INF/squbs-meta.&lt;ext&gt; in each classpath entry.
If squbs metadata is available, the jar is treated as squbs cube or extension and initialized according to the
metadata declarations. The bootstrap then first initializes extensions, cubes, then service routes last regardless of
their sequence in the classpath.

#Shutting Down squbs

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

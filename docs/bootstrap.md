#Unicomplex & Cube Bootstrapping

squbs comes with a default bootstrap class `org.squbs.unicomplex.Bootstrap`. This can be started from IDEs, command line, sbt, or even Maven. Bootstrap scans the class loader and finds META-INF/squbs-meta.&lt;ext&gt; in each loaded jar resource.
If squbs metadata is available, the jar resource is treated as squbs cube or extension and initialized according to the metadata declarations. The bootstrap then first initializes extensions, cubes, then service handlers last regardless of
their sequence in the classpath.

Given normal circumstances, bootstrapping detail are of not much significance. However, one may need to programmatically bootstrap squbs in different ways. This is especially common in test cases needing custom configuration and needing to run in parallel. Please see [Testing](testing.md) for more information. The syntax for bootstrapping squbs is as follows:

**Option 1)** Start with user-defined configuration

```
UnicomplexBoot(customConfig)
  .createUsing {(name, config) => ActorSystem(name, config)}
  .scanResources()
  .initExtensions
  .stopJVMOnExit
  .start()
```

**Option 2)** Start with default configuration

```
UnicomplexBoot {(name, config) => ActorSystem(name, config)}
  .scanResources()
  .initExtensions
  .stopJVMOnExit
  .start()  
```

Lets take a look at each component.

1. Creating the UnicomplexBoot (boot) object. This can be done by passing a custom config or an actor system creator closure to `UnicomplexBoot.apply()`.

2. The configuration object shown as `customConfig` in the example above. This is a configuration object obtained from the Typesafe Config library's parse functions. This config object is not yet merged with `reference.conf`. It is optional and substitutes other `application.conf` configurations defined.

3. The ActorSystem creator passes a function or closure to create the ActorSystem. The actual creation happens in the start phase (item 7, below). The default function is `{(name, config) => ActorSystem(name, config)}`. The input name is the intended ActorSystem name read from the configuration. The config is the loaded configuration object after merging with any provided config. Most use cases would want to create the ActorSystem this way and thus the function need not be provided. `createUsing` can be avoided altogether.

4. Scanning components looking for cubes, services, or extensions using the `scanResources()` function. This is
mandatory as there would otherwise be no components to start. If no arguments are passed, the squbs bootstrap will scan its class loader. 
Test cases may want to target certain components to scan only. This can be done by passing the location of additional
`squbs-meta.conf` file locations (as a variable argument to scanResources), such as
`.scanResources("component1/META-INF/squbs-meta.conf", "component2/META-INF/squbs-meta.conf")`. This will scan your
classpath and the given resources in addition. If you do not want classpath to be scanned, pass
`withClassPath = false` or just `false` as the first argument before the resource list: 
`.scanResources(withClassPath = false, "component1/META-INF/squbs-meta.conf", "component2/META-INF/squbs-meta.conf")`

5. Initialize the extensions using the `initExtension` function. This will initialize the extensions scanned. Extension initialization is done before the ActorSystem is created. For multiple Unicomplex cases (multiple ActorSystems), the same extension must not be initialized more than once. An extension can only be used by one test case. In some test cases we do not want to initialize the extensions at all and would not call `initExtension` altogether.

6. Stopping the JVM on exit. This is enabled by calling the `stopJVMOnExit` function. This option should generally not be used for test cases. It is used by squbs' bootstrap to make sure squbs shuts down and exits properly.

7. Starting the Unicomplex by calling `start()`. This is a mandatory step. Without it no ActorSystem starts and no 
Actor would be able to run. The start call blocks until the system is fully up and running, or a timeout occurs.
When start times out, some components may still be initializing, leaving the system in `Initializing` state. However,
any single component failure will flip the system state to `Failed` at timeout. This would allow for system components
like system diagnostics to run and complete. The default start timeout is set to 60 seconds. For tests that expect
timeouts, it can be set lower by passing the desired timeout as an argument to `start()` such as
`start(Timeout(5 seconds))` or in short `start(5 seconds)` using implicit conversions from duration to a timeout.


#Configuration Resolution

squbs chooses one application configuration and merges it with the aggregated application.conf and reference.conf in the classpath. The application configuration being merged are chosen from the following order.

1. If a configuration is provided when creating the boot object, this configuration is chosen. This is the `customConfig` field from the example above.

2. If an `application.conf` file is provided in the external config directory, this `application.conf` is chosen. The external config dir is configured by setting the config property `squbs.external-config-dir` and defaults to `squbsconfig`. Not that this directory cannot be changed or overridden by supplied configuration or an external configuration (as the directory itself is determined using the config property.)

3. Otherwise, the `application.conf` provided with the application, if any, will be used. This then falls back to the `reference.conf`.

#Drop-in Modular System 

squbs divides applications into modules called cubes. Modules in squbs are intended to be run in modular isolation as
well as on a flat classpath. Modular isolation is intended for true loose coupling of the modules, without incurring
any classpath conflicts due to the dependencies.

The current implementation bootstraps from a flat classpath. On bootstrapping, squbs will automatically detect the modules by classpath scanning. Scanned cubes are detected and started automatically.

##Cube Jars

All cubes are represented by a top-level jar file with the cube logic itself. All cubes must have the cube metadata
residing in META-INF/squbs-meta.&lt;ext&gt;. Supported extensions are .conf, .json, and .properties. The format follows the
[Typesafe config](https://github.com/typesafehub/config) format.

At the minimum, the cube metadata uniquely identifies the cube and version and declares and configures one or more of
the followings:

*Actor*: Identifies the well known actors automatically started by squbs.

*Service*: Identifies a squbs service.

*Extension*: Identifies a squbs framework extension. The extension entry point has to extend from `org.squbs.lifecycle.ExtensionLifecycle` trait.
    
##Configuration Resolution

Providing `application.conf` for a cube may cause issues when multiple cubes try to provide their internal `application.conf`. The precedence rules for merging such configuration is undefined. It is recommended cubes only provide a `reference.conf` and can be overridden by an external `application.conf` for deployment.

##Well Known Actors

Well known actors are just [Akka actors](http://doc.akka.io/docs/akka/2.3.13/scala/actors.html) as defined by the
[Akka documentation](http://doc.akka.io/docs/akka/2.3.13/scala.html). They are started by a supervisor actor that is created for each cube. The supervisor carries the name of the cube. Therefore any well known actor has a path of
/&lt;CubeName&gt;/&lt;ActorName&gt; and can be looked up using the ActorSelection call under /user/&lt;CubeName&gt;/&lt;ActorName&gt;.

A well known actor can be started as a singleton actor or with a router. To declare a well known actor as a router,
add:
    with-router = true
in the actor declaration. Router, dispatcher, and mailbox configuration for well known actors are done in
reference.conf or application.conf following the Akka documentation.

Following is a sample cube declaration META-INF/squbs-meta.conf declaring a well known actor:

```
cube-name = org.squbs.bottlecube
cube-version = "0.0.2"
squbs-actors = [
  {
    class-name = org.squbs.bottlecube.LyricsDispatcher
    name = lyrics
    with-router = false  # Optional, defaults to false
    init-required = false # Optional
  }
]
```

The `init-required` parameter is used for actors that need to signal back their fully initialized status for the system to be considered initialized. Please refer to the [Startup Hooks](lifecycle.md#startup-hooks) section of the [Runtime Lifecycles & API](lifecycle.md) documentation for a full discussion of startup/initialization hooks.

If an actor is configured `with-router` (with-router = true) and a non-default dispatcher, the intention is usually to
schedule the actor (routee) on the non-default dispatcher. The router will assume the well known actor name, not the
routee (your actor implementation). A dispatcher set on the router will only affect the router, not the routee. To
affect the routee, you need to create a separate configuration for the routees and append "/*" to the name. Next you want to
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
[Akka documentation](http://doc.akka.io/docs/akka/2.3.13/scala/routing.html).

##Services

Each service entry point is bound to a unique web context which is the leading path segments separated by the `/` character. For instance, the url `http://mysite.com/my-context/index` would match the context `"my-context"`, if registered. It can also match the root context if `"my-context"` is not registered. Web contexts are not necessarily the first slash-separated segment of the path. Dependent on the context registration, it may match multiple such segments. A concrete example would be a URL with service versioning. The URL `http://mysite.com/my-context/v2/index` may have either `my-context` or `my-context/v2` as the web context, depending on what contexts are registered. If both `my-context` and `my-context/v2` are registered, the longest match - in this case `my-context/v2` will be used for routing the request.

Service implementations can have two flavors:

1. A spray-can style server request handler actor as documented at [http://spray.io/documentation/1.2.3/spray-can/http-server/](http://spray.io/documentation/1.2.3/spray-can/http-server/). The actor handles all but the `Connected` message and must not take any constructor arguments. The whole request or request part is passed on to this actor unchanged.

2. A route definition defining your `Route`. These are classes extending from the `org.squbs.unicomplex.RouteDefinition` trait, or `org.squbs.unicomplex.streaming.RouteDefinition` trait if you use Akka Http. They must not take any constructor arguments (zero-argument constructor) and have to provide the route member which is a Spray route according to the
   [spray-routing documentation](http://spray.io/documentation/1.2.3/spray-routing/key-concepts/routes/) or Akka Http route according to the Akka [High-level Server-Side API](http://doc.akka.io/docs/akka/2.4/scala/http/routing-dsl/index.html). In contrast to the actor implementation, the path matching of the route matches the path **AFTER** the registered web context. For instance, a route definition registered under the web context `"my-context"` would match `/segment1/segment2` for the url `http://mysite.com/my-context/segment1/segment2` not including the web context string itself.
   
3. A flow definition providing your `Flow[HttpRequest, HttpResponse, NotUsed]` by extending from the `org.squbs.unicomplex.streaming.FlowDefinition` trait. The flow definition only works with Akka Http, not with Spray. The `HttpRequest` path in the flow is the full path no matter the web context this service is serving.
      
Service metadata is declared in META-INF/squbs-meta.conf as shown in the following example.

```
cube-name = org.squbs.bottlesvc
cube-version = "0.0.2"
squbs-services = [
  {
    class-name = org.squbs.bottlesvc.BottleSvc
    web-context = bottles # You can also specify bottles/v1, for instance.
    
    # The listeners entry is optional, and defaults to 'default-listener'.
    listeners = [ default-listener, my-listener ]
    
    # Optional, defaults to a default pipeline.
    pipeline = some-pipeline
    
    # Optional, disables the default pipeline if set to false.
    defaultPipelineOn = true                
    
    # Optional, only applies to actors.
    init-required = false
  }
]
```

The class-name parameter identifies either the actor, `RouteDefinition`, or `FlowDefinition` class.

The web-context is a string that uniquely identifies the web context of a request to be dispatched to this service. It **MUST NOT** start with a `/` character. It can have `/` characters inside as segment separators in case of multi-segment contexts. And it is allowed to be `""` for root context. If multiple services match the request, the longest context match takes precedence.

Optionally, the listeners parameter declares a list of listeners to bind this service. Listener binding is discussed in the following section, below.

The pipeline is a set of request pre-processors before the request gets processed by the request handler. The pipeline name can be specified by a `pipeline` parameter. A configured default pipeline will be used. To disable the default pipeline for this service, you can set `defaultPipelineOn = false` in META-INF/squbs-meta.conf. Please refer to [Streaming Request/Response Pipeline](streamingpipeline.md) for more information.

Only actors can have another optional `init-required` parameter which allows the actor to feed back its state to the system. Please refer to the [Startup Hooks](lifecycle.md#startup-hooks) section of the [Runtime Lifecycles & API](lifecycle.md) documentation for a full discussion of startup/initialization hooks.


### Listener Binding

A listener is declared in `application.conf` or `reference.conf` usually living in the project's `src/main/resources` directory. Listeners declare interfaces, ports, and security attributes, and name aliases, and are explained in [Configuration](configuration.md)

A service handler attaches itself to one or more listeners. The `listeners` attribute is a list of listeners or aliases the handler should bind to. If listeners are not defined, it will default to the `default-listener`.

The wildcard value `"*"` (note, it has to be quoted or will not be properly be interpreted) is a special case which translates to attaching this handler to all active listeners. By itself, it will however not activate any listener if it is not already activated by a concrete attachment of a handler. If the handler should activate the default listener and attach to any other listener activated by other handlers, the concrete attachment has to be specified separately as follows:

```
    listeners = [ default-listener, "*" ]
```

### Runtime Access to Web Context
While the web context is configured in metadata, the route, the actor, and the flow will sometimes need to know what web context it is serving. To do so, any service handler class (route, actor, flow) may want to mix in the `org.squbs.unicomplex.WebContext` trait. Doing so will add the following field to your object

```
    val webContext: String
```

The webContext field is initialized to the value of the configured web context as set in Metadata upon construction of the object as shown below:

```
class MySvcActor extends Actor with WebContext {
  def receive = {
    case HttpRequest(HttpMethods.GET, Uri.Path(p), _, _, _)
        if p.startsWith(s"/$webContext") =>
      // handle request...
  }
}
```

The `webContext` field will automatically be made available to this class

##Extensions

Extensions for squbs are low level facilities that need to be started for the environment. The extension initializer
has to extend from the `org.squbs.lifecycle.ExtensionLifecycle` trait and override the proper callbacks. An extension
has great capabilities to introspect the system and provide additional functionality squbs by itself does not provide.
An extension must not be combined with an actor or a service in the same cube.

Extensions are started serially, one after another. Providers of extensions can provide a sequence number for the extension startup by specifying:
    sequence = [number]
in the extension declaration. If the sequence is not specified, it defaults to Int.maxValue. This means it will start
after all extensions that provide a sequence number. If there is more than one extension not specifying the sequence or specifying the same sequence number,
the order between starting these is indeterministic. The shutdown order is the reverse of the startup order.

#Shutting Down squbs

The squbs runtime can be properly shutdown by sending the `Unicomplex()` a `GracefulStop` message. 
 
The default startup main method, `org.squbs.unicomplex.Bootstrap`, registers a JVM shutdown hook that sends a `GracefulStop` message to `Unicomplex`.  Accodingly, if a squbs app is started by using the default main method, the system will be shutdown gracefully when JVM receives a `SIGTERM`.  

If some other monitor process is responsible for shutting down the app, e.g. JSW, `org.squbs.unicomplex.Shutdown` can be set as the main method to gracefully shutdown the system.  This `Shutdown` main method sends a `GracefulStop` message to `Unicomplex` as well.

In some use cases, it is desirable to add a delay to the shutdown.  For instance, if a load balancer checks the health of the application every 5 seconds and the app is shutdown 1 second after a health check, the app will keep getting requests for the next 4 seconds until the next health check; however, it won't be able to serve these requests.  If you are using one of the methods above, `org.squbs.unicomplex.Bootstrap` or `org.squbs.unicomplex.Shutdown`, you can add a delay to shutdown by adding the following in configuration: 

```
squbs.shutdown-delay = 5 seconds
```

With the above configuration, the `GracefulStop` message to `Unicomplex` will be scheduled to be sent with a 5 second delay. 

After receiving the `GracefulStop` message, the `Unicomplex` actor will stop the service and propagate the `GracefulStop`
message to all cube supervisors. Each supervisor will be responsible for stopping the actors in its cube 
(by propagating the `GracefulStop` message to its children who wants to perform a graceful stop), 
ensure they stopped successfully or re-send a `PoisonPill` after timeout, and then stop itself. 
Once all cube supervisors and services are stopped, the squbs system shuts down. Then, a shutdown hook will be
invoked to stop all the extensions and finally exits the JVM.

There is currently no standard console to a web container allowing users of squbs to build their own. The web console could
provide proper user shutdown by sending a stop message to Unicomplex as follows:

```
  Unicomplex() ! GracefulStop
```

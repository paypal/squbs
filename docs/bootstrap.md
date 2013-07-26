Bootstrapping squbs
===================

squbs divides applications into modules called cubes. Modules in squbs are intended to be run in modular frameworks like OSGi as well as on a flat classpath (please read OSGi benefits below). The current implementation bootstraps from a flat classpath.


OSGi
-------------
OSGi adds the full isolation of each cube's dependencies and ensures they are non-conflicting, especially if they come from different origins. It also allows for the opportunity to hot-upgrade the cubes. Cubes are only accessed through ActorRefs so they MUST NOT export any packages. This avoids any potential for the 'uses' conflict - the most complex and hardest to debug OSGi issue. The downside for OSGi is its additional runtime complexity.


Cube Jars
---------
Cube jars are assumed to be fully qualified OSGi bundles without any export packages. The squbs bootstrapping process identifies the jars by the bundle symbolic name, version, and additional X-squbs custom headers in the jar's MANIFEST.MF. Note that this metadata is required whether or not we're running in OSGi mode. The following are the squbbs custom manifest headers:

*X-squbs-actors*: Actors that need to be bootstrapped as part of bringing up the cube. The entries list the class name and an optional (but recommended) name parameter for the actor. Each actor class MUST be implementing the Actor trait. Entries are comma separated and options are separated from the entry name and other options by semicolon. If the name parameter is not provided, the actor class name without the package is used as the actor name. The actor is started under a cube supervisor ensuring proper fault handling and a actor path not conflicting with anoher cube. If a cube is undeployed, the standard actor lifecycle is used for destroying the actors. The supervisor carries the name of the cube(without any groupId or package prefixes). For instance, the actor registration for the bottlecube in the bottle sample is as follows:
    X-squbs-actors: org.squbs.bottlecube.LyricsDispatcher;name=lyrics


*X-squbs-services*: Web service RouteDefinitions to be registered as part of bringing up the cube. Each service is a class that implements the RouteDefition trait defined by squbs. On undeployment of the cube, the service is automatically unregistered. The service manifest entry for the bottlesvc sample is as follows:
    X-squbs-services: X-squbs-services: org.squbs.bottlesvc.BottleSvc

Extensions
----------
Extensions for squbs are arbitrary facilities that need to be started for the environment. The startup logic is defined in a scala object and the object is registered using the custom manifest header:

*X-squbs-extensions*: The X-squbs-extensions manifest headers for jars containing extensions lists the scala objects to be loaded and started in the runtime environment. The list is comma separated.

Bootstrapping in non-OSGi mode
------------------------------
Bootstrapping squbs is done by starting the org.squbs.unicomplex.Bootstrap object from the Java command line, IDE, or maven plugin. Bootstrap scans the classpath and finds META-INF/MANIFEST.MF in each classpath entry. If available and has the X-squbs manifest headers, the entry is treated as squbs cube or extension (or both) and initialized according to the header. The bootstrap scans for these headers and first initializes extensions, cubes, then service routes last regardless of their sequence in the classpath.

Shutting Down squbs
-------------------
At this point in time, we just terminate the process to shutdown squbs. This attribute of squbs will be improved over time to provide a proper shutdown.

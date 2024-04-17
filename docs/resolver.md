# Resource Resolution

Given that very few - if any - real life applications can work without external resources, environment aware resource resolution is becoming a crucial part of application infrastructure. squbs provides resource resolution through the `ResolverRegistry` and allows resources of any type to be resolved by name and environment. The latter allows differentiation of a resource between production, qa, and dev environments.

Example of resource resolutions are HTTP endpoints, messaging endpoints, databases. All of these are handled by one single registry.

### Dependency

The resolver sits in `squbs-ext`. Add the following dependency to your `build.sbt` or scala build file:

```scala
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The basic usage of the `Resolver` is for looking up resources. A type needs to be provided as the registry can hold resources of multiple types such as HTTP endpoints, messaging endpoints, or database connections. We use the type `URI` in our samples in this documentation. A lookup call is shown in the followings:

##### Scala

```scala
// To resolve a resource for a specific environment.
val resource: Option[URI] = ResolverRegistry(system).resolve[URI]("myservice", QA)

```

##### Java

```java
// To resolve a resource for a specific environment.
val resource: Optional<URI> = ResolverRegistry.get(system).resolve(URI.class, "myservice", QA.value());
```

### The ResolverRegistry

The `ResolverRegistry` is an Pekko extension and follows the Pekko extension usage patterns in Scala and in Java. It can host resource resolvers of various types and therefore the resource type has to be provided at registration by passing it to the `register` call. Multiple resolvers of same type and multiple types can be registered.

#### Registering Resolvers

There are two styles of APIs provided for resolver registration. One is a shortcut API allowing passing a closure or lambda as the resolver. The closure's or lambda's return type has to be `Option[T]` for Scala and `Optional<T>` for Java. The other full API takes a `Resolver[T]` in Scala or an `AbstractResolver<T>` in Java, `T` being the resource type. These can be seen in the followings:

##### Scala

```scala
// To register a new resolver for type URI using a closure. Note the return
// type of the closure must be `Option[T]` or in this case `Option[URI]`
ResolverRegistry(system).register[URI]("MyResolver") { (svc, env) =>
  (svc, env) match {
    case ("myservice", QA) => Some(URI.create("http://myservice.qa.mydomain.com"))
    case ("myservice", Default) => Some(URI.create("http://myservice.mydomain.com"))
    case ("myservice2", QA) => Some(URI.create("http://myservice2.qa.mydomain.com"))
    case ("myservice2", Default) => Some(URI.create("http://myservice2.mydomain.com"))
    case _ => None
  }
}

// To register a new resolver for type URI by extending the `Resolver` trait
class MyResolver extends Resolver[URI] {
  def name: String = "MyResolver"
  
  def resolve(svc: String, env: Environment = Default): Option[URI] = {
    (svc, env) match {
      case ("myservice", QA) => Some(URI.create("http://myservice.qa.mydomain.com"))
      case ("myservice", Default) => Some(URI.create("http://myservice.mydomain.com"))
      case ("myservice2", QA) => Some(URI.create("http://myservice2.qa.mydomain.com"))
      case ("myservice2", Default) => Some(URI.create("http://myservice2.mydomain.com"))
      case _ => None
    }
  }
}

// Then just register the instance
ResolverRegistry(system).register[URI](new MyResolver)
```

##### Java 

```java
// To register a new resolver for type URI using a lambda. Note the return
// type of the lambda must be `Optional<T>` or in this case `Optional<URI>`
ResolverRegistry.get(system).register("MyResolver", (svc, env) -> {
    if ("myservice".equals(svc)) {
        if (QA.value().equals(env)) {
          return Optional.of(URI.create("http://myservice.qa.mydomain.com"));
        } else {
          return Optional.of(URI.create("http://myservice.mydomain.com"));
        }
    } else if ("myservice2".equals(svc)) {
        if (QA.value().equals(env)) {
          return Optional.of(URI.create("http://myservice2.qa.mydomain.com"));
        } else {
          return Optional.of(URI.create("http://myservice2.mydomain.com"));
        }    
    } else {
        return Optional.empty();
    }
});

// To register a new resolver for type URI by extending an abstract class
public class MyResolver extends AbstractResolver<URI> {
    @Override
    public String name() {
        return "MyResolver";
    }
    
    @Override
    public Optional<URI> resolve(String svc, Environment env) {
        if ("myservice".equals(svc)) {
            if (QA.value().equals(env)) {
                return Optional.of(URI.create("http://myservice.qa.mydomain.com"));
            } else {
                return Optional.of(URI.create("http://myservice.mydomain.com"));
            }
        } else if ("myservice2".equals(svc)) {
            if (QA.value().equals(env)) {
                return Optional.of(URI.create("http://myservice2.qa.mydomain.com"));
            } else {
                return Optional.of(URI.create("http://myservice2.mydomain.com"));
            }    
        } else {
            return Optional.empty();
        }
    }
}

// Then register MyResolver.
ResolverRegistry.get(system).register(URI.class, new MyResolver());
```

#### Discovery Chain

The resource discovery follows a LIFO model. The most-recently registered resolver takes precedence over previously registered ones. The `ResolverRegistry` walks the chain one by one until there is a resolver compatible with the given type that provides the resource or the chain has been exhausted. In that case the registry will return a `None` for the Scala API and a `Optional.empty()` for the Java API.

#### Type Compatibility

The `ResolverRegistry` checks the requested type at the time of the `resolve` call. If the type of the registered resolver is the same type or a subtype of the requested type, that resolver will be used to try resolve the resource by name.

Due to JVM type erasure, type parameters of the registered or requested types are not accounted for. For instance, a registration of type `java.util.List<String>` may be matched by a `resolve` call of type `java.util.List<Int>` as the type parameter `String` or `Int` is erased at runtime. Due to this limitation, using types with type parameters for registration and lookup is highly discouraged. The results are undefined - you may just get the wrong resource.

For simplicity, it is highly encouraged not to make use of type hierarchies. All registered types should be distinct types.

#### Resolving for a Resource

Similar to the registration, the resolution requires a type compatible with the registered type; the registered type has to be the same or a subtype of the resolution type.

##### Scala

```scala
// To resolve a resource with `Default` environment.
val resource: Option[URI] = ResolverRegistry(system).resolve[URI]("myservice")

// To resolve a resource for a specific environment.
val resource: Option[URI] = ResolverRegistry(system).resolve[URI]("myservice", QA)
```

##### Java

```java
val resource: Optional<URI> = ResolverRegistry.get(system).resolve(URI.class, "myservice", QA.value());
```

#### Un-registering a Resolver

Un-registering is done by name using the following API.

##### Scala

```scala
ResolverRegistry(system).unregister("MyResolver")
```

##### Java

```java
ResolverRegistry.get(system).unregister("MyResolver");
```

#### Concurrency considerations

Resolver registration and un-registration calls are expected to be done in a non-concurrent manner at initialization time. There is no safeguard for concurrent registrations and hence the results of concurrent registrations are undefined. Your resolver may or may not get registered on a concurrent registration or un-registration.

Resolve calls are however thread safe and can be accessed concurrently without limitations at the `ResolverRegistry` level. Each individual registered resolver needs to be thread-safe.
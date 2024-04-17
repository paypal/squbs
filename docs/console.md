# Admin Console

The squbs admin console provides a web/JSON interface into system state and statistics of both squbs and the JVM. All state and statistics of squbs and the JVM is provided as JMX MXBeans. Cubes, applications, or components are free to register their own monitors.

**Note**: The squbs admin console does not allow JMX method execution or modifications of any settings just yet.

## Dependencies

To use the squbs admin console, simply add the following dependencies in your build.sbt file or Scala build script:

```
  "org.squbs" %% "squbs-admin" % squbsVersion
```

squbs-admin will be auto-detected. No API is required.

## Accessing Bean Information

squbs-admin uses the context `/adm`. Just hitting `/adm` alone will list out the catalog of JMX MXBeans and the corresponding URLs to access the beans as in the following example:

```json
{
  "JMImplementation:type=MBeanServerDelegate" : "http://localhost:8080/adm/bean/JMImplementation:type~MBeanServerDelegate",
  "com.sun.management:type=DiagnosticCommand" : "http://localhost:8080/adm/bean/com.sun.management:type~DiagnosticCommand",
  "com.sun.management:type=HotSpotDiagnostic" : "http://localhost:8080/adm/bean/com.sun.management:type~HotSpotDiagnostic",
  "java.lang:type=ClassLoading" : "http://localhost:8080/adm/bean/java.lang:type~ClassLoading",
  "java.lang:type=Compilation" : "http://localhost:8080/adm/bean/java.lang:type~Compilation",
  "java.lang:type=GarbageCollector,name=PS MarkSweep" : "http://localhost:8080/adm/bean/java.lang:type~GarbageCollector,name~PS%20MarkSweep",
  "java.lang:type=GarbageCollector,name=PS Scavenge" : "http://localhost:8080/adm/bean/java.lang:type~GarbageCollector,name~PS%20Scavenge",
  "java.lang:type=Memory" : "http://localhost:8080/adm/bean/java.lang:type~Memory",
  "java.lang:type=MemoryManager,name=CodeCacheManager" : "http://localhost:8080/adm/bean/java.lang:type~MemoryManager,name~CodeCacheManager",
  "java.lang:type=MemoryManager,name=Metaspace Manager" : "http://localhost:8080/adm/bean/java.lang:type~MemoryManager,name~Metaspace%20Manager",
  "java.lang:type=MemoryPool,name=Code Cache" : "http://localhost:8080/adm/bean/java.lang:type~MemoryPool,name~Code%20Cache",
  "java.lang:type=MemoryPool,name=Compressed Class Space" : "http://localhost:8080/adm/bean/java.lang:type~MemoryPool,name~Compressed%20Class%20Space",
  "java.lang:type=MemoryPool,name=Metaspace" : "http://localhost:8080/adm/bean/java.lang:type~MemoryPool,name~Metaspace",
  "java.lang:type=MemoryPool,name=PS Eden Space" : "http://localhost:8080/adm/bean/java.lang:type~MemoryPool,name~PS%20Eden%20Space",
  "java.lang:type=MemoryPool,name=PS Old Gen" : "http://localhost:8080/adm/bean/java.lang:type~MemoryPool,name~PS%20Old%20Gen",
  "java.lang:type=MemoryPool,name=PS Survivor Space" : "http://localhost:8080/adm/bean/java.lang:type~MemoryPool,name~PS%20Survivor%20Space",
  "java.lang:type=OperatingSystem" : "http://localhost:8080/adm/bean/java.lang:type~OperatingSystem",
  "java.lang:type=Runtime" : "http://localhost:8080/adm/bean/java.lang:type~Runtime",
  "java.lang:type=Threading" : "http://localhost:8080/adm/bean/java.lang:type~Threading",
  "java.nio:type=BufferPool,name=direct" : "http://localhost:8080/adm/bean/java.nio:type~BufferPool,name~direct",
  "java.nio:type=BufferPool,name=mapped" : "http://localhost:8080/adm/bean/java.nio:type~BufferPool,name~mapped",
  "java.util.logging:type=Logging" : "http://localhost:8080/adm/bean/java.util.logging:type~Logging",
  "org.squbs.unicomplex:type=CubeState,name=admin" : "http://localhost:8080/adm/bean/org.squbs.unicomplex:type~CubeState,name~admin",
  "org.squbs.unicomplex:type=Cubes" : "http://localhost:8080/adm/bean/org.squbs.unicomplex:type~Cubes",
  "org.squbs.unicomplex:type=Extensions" : "http://localhost:8080/adm/bean/org.squbs.unicomplex:type~Extensions",
  "org.squbs.unicomplex:type=Listeners" : "http://localhost:8080/adm/bean/org.squbs.unicomplex:type~Listeners",
  "org.squbs.unicomplex:type=ServerStats,listener=default-listener" : "http://localhost:8080/adm/bean/org.squbs.unicomplex:type~ServerStats,listener~default-listener",
  "org.squbs.unicomplex:type=SystemSetting" : "http://localhost:8080/adm/bean/org.squbs.unicomplex:type~SystemSetting",
  "org.squbs.unicomplex:type=SystemState" : "http://localhost:8080/adm/bean/org.squbs.unicomplex:type~SystemState"
}
```

JSON plugins for your browser would detect and allow easy clicking on the links. Following the provided links, for instance, bean `org.squbs.unicomplex:type=Cubes` will show all bean detail as follows:

```json
{
  "Cubes" : [
    {
      "fullName" : "org.squbs.admin",
      "name" : "admin",
      "supervisor" : "Actor[pekko://squbs/user/admin#104594558]",
      "version" : "0.7.1"
    }
  ]
}
```

## Bean URL & Encoding

All beans are under the `/adm/bean/` path, followed by the full bean object name. The bean object name gets translated as follows to make it possible for easy URL access:

1. The `=` in the bean name is replaced with `~`.
2. All other characters are encoded according to standard URL encoding. For instance, a space in the name is encoded as `%20`.

For instance, to access the bean with object name `java.lang:type=GarbageCollector,name=PS Scavenge`, the URL path would be `/adm/bean/java.lang:type~GarbageCollector,name~PS%20Scavenge`

## Listener

squbs-admin binds to the `admin-listener`. By default, this is an alias of the `default-listener`. Services can override the Unicomplex configuration and re-assign the `admin-listener` alias to any other defined listener of choice. The following example shows how to re-assign aliases in your `application.conf`:

Alias defined in Unicomplex

```
default-listener {
  aliases = [admin-listener]
}
```

An example override in `application.conf`

```
default-listener {
  aliases = []
}

my-listener {
  aliases = [admin-listener]
}
```

## Exclusions

In many cases, an MXBean may contain information deemed sensitive, and should not be displayed in the admin console. The best way around this is, of course, to not include such information into a JMX bean in the first place. However, sometimes these beans are not exposed by your component but by a third party component or library and there is no way to hide such information short of modifying the third party component. Exclusions are a feature provided by the admin console to hide such information from showing in the JSON.

### Configuring Exclusions

Exclusions are configured under squbs.admin in your standard configuration in `application.conf` as can be seen in the following example:

```
squbs.admin {
  exclusions = [
    "java.lang:type=Memory",
    "java.lang:type=GarbageCollector,name=PS MarkSweep::init",
    "java.lang:type=GarbageCollector,name=PS MarkSweep::startTime"
  ]
}
```

* To exclude a whole MXBean from the console's view, list the bean object name in the exclusions. 
* To exclude a field or property in an MXBean, list the bean object name and the field name in the exclusions.
  * The bean object name and field name are separated by `::`.
  * The field name acts as a filter of any attribute or field at any depth in the MXBean. If the field name matches the provided name, it will be excluded.
* Multiple field exclusion on the same bean can be achieved by listing `beanName::field1`, `beanName::field2`, and so forth.

## Error Responses
A 404 (NotFound) response is provided on a request to obtain an invalid bean object name, or a name of a blacklisted bean. The results of either case is indistinguishable for security reasons.

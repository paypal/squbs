# Monitoring Actors at Runtime

## Overview

The squbs-actormonitor module attaches monitoring to each actor in the actor system. For large number of actors, this can get intrusive. The number of actors to monitor can be configured through `application.conf`. Use judgement attaching this module in production. There is no user API to this module.

## Dependencies

Add the following dependency to your build.sbt or scala build file:

```
"org.squbs" %% "squbs-actormonitor" % squbsVersion
```

## Monitoring

Each actor has a corresponding JMXBean(org.squbs.unicomplex:type=ActorMonitor,name=%actorPath) to expose the actor information:

```
 trait ActorMonitorMXBean {
  def getActor: String
  def getClassName: String
  def getRouteConfig : String
  def getParent: String
  def getChildren: String
  def getDispatcher : String
  def getMailBoxSize : String
}
```

## Configuration

The following is the configuration entries for squbs-actormonitor:

```
squbs-actormonitor = {
  maxActorCount = 500
  maxChildrenDisplay = 20
}
```

A JMX Bean `org.squbs.unicomplex:type=ActorMonitor` exposes the configuration of Actor Monitor. The JMX Bean is read-only.

```
 trait ActorMonitorConfigMXBean {
  def getCount : Int				//Count of JMX bean has been created 
  def getMaxCount: Int				//Maximum JMX bean can be created
  def getMaxChildrenDisplay: Int		//Per each actor, maximum children can be exposed 
 }
 ```
 


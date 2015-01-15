##Overview

Monitoring status of each actor under actor system path "/user/*"

* Each actor has a corresponding JMXBean(org.squbs.unicomplex:type=ActorMonitor,name=%actorPath) to expose the actor information:
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


* A JMX Bean(org.squbs.unicomplex:type=ActorMonitor) to expose the configuration of Actor Monitor
```
 trait ActorMonitorConfigMXBean {
  def getCount : Int				//Count of JMX bean has been created 
  def getMaxCount: Int				//Maximum JMX bean can be created
  def getMaxChildrenDisplay: Int		//Per each actor, maximum children can be exposed 
 }
 ```
 

## Dependencies

Add the following dependency to your build.sbt or scala build file:

"org.squbs" %% "squbs-actormonitor" % squbsVersion

# Timeout Policy

Timeouts are a key part of every asynchronous system and every distributed system. They are usually configured statically and hard to get right. squbs provides the Timeout Policy facility as part of the squbs-pattern package to help dynamically determine the correct timeout given a policy instead of static setting.  This dynamically determined timeout value from the policy can then be utilized for setting the timeouts.  Please note that timeout policy helps only in deriving timeout values and its usage itself will not cause the timeouts to occur.

## Dependencies

Add the following dependency to your build.sbt or scala build file:

```scala
"org.squbs" %% "squbs-pattern" % squbsVersion
```

## Quick Example

In a normal case of awaits as shown in the Scala code below...

```scala
Await.ready(future, timeout.duration)
```

You can change this code to an await block insude a closure as follows:

```scala
val policy = TimeoutPolicy(name = Some("mypolicy"), initial = 1 second)
val result = policy.execute(duration => {
  Await.ready(future, duration)
})
```

## Scala API

You can create the TimeoutPolicy by just passing a rule to it as follows:

1. For fixed timeout you actually do not have to specify a rule. The fixedRule is the default.

   ```scala
   val policy = TimeoutPolicy(name = Some("MyFixedPolicy"), initial = 1 second, rule = fixedRule)
   ```

2. Timeout based on standard deviation of response times (sigma). 

   ```scala
   val policy = TimeoutPolicy(name = Some("MySigmaPolicy"), initial = 1 second, rule = 3 sigma)
   ```

3. Timeout based on percentiles of response times.

   ```scala
   val policy = TimeoutPolicy(name = Some("MyPctPolicy"), initial = 1 second, rule = 95 percentile)
   ```

Then, you can use the policy as follows:

```scala
val result = policy.execute(duration => {
  Await.ready(future, duration)
})
```

Or another form where we do not use a closure.


```scala
val policy = TimeoutPolicy(name = Some("mypolicy"), initial = 1 second)
val tx = policy.transaction
Await.ready(future, tx.waitTime)
tx.end
```

The `tx.end` is important as it provides closure to a single operation watched by the timeout policy, something that is automatically detected when using the preferred closure version of the API. This is used as a feedback loop to observe actual execution times and feed this information back into the heuristics.

## Java API

In the Java API, we create the timeout policy using a policy builder, as can be seen in the following examples...

1. For fixed timeout.

   ```java
   TimeoutPolicy fixedTimeoutPolicy = TimeoutPolicyBuilder
       .create(new FiniteDuration(INITIAL_TIMEOUT, MILLISECONDS), fromExecutorService(es))
       .minSamples(1)
       .rule(TimeoutPolicyType.FIXED)
       .build();
   ```

2. Timeout based on standard deviation of response times (sigma).
 
   ```java
   TimeoutPolicy sigmaTimeoutPolicy = TimeoutPolicyBuilder
       .create(new FiniteDuration(INITIAL_TIMEOUT, MILLISECONDS), system.dispatcher())
       .minSamples(1)
       .name("MySigmaPolicy")
       .rule(3.0, TimeoutPolicyType.SIGMA)
       .build();
   ```

3. Timeout based on percentile of response times.

   ```java
   TimeoutPolicy percentileTimeoutPolicy = TimeoutPolicyBuilder
       .create(new FiniteDuration(INITIAL_TIMEOUT, MILLISECONDS), system.dispatcher())
       .minSamples(1)
       .name("PERCENTILE")
       .rule(95, TimeoutPolicyType.PERCENTILE)
       .build();
   ```

Then, to use the timeout policy, just execute your timed call inside the closure as follows:

```java
policy.execute((FiniteDuration t) -> {
    return es.submit(timedCall).get(t.toMillis() + 20, MILLISECONDS);
});
```

Or, you can use the non-closure version of the call as follows:

```java
TimeoutPolicy.TimeoutTransaction tx = policy.transaction();
try {
  return timedFn.get(tx.waitTime());
} catch (Exception e) {
  System.out.println(e);
} finally {
  tx.end();
}
```

The `tx.end` is important as it provides closure to a single operation watched by the timeout policy, something that is automatically detected when using the preferred closure version of the API. This is used as a feedback loop to observe actual execution times and feed this information back into the heuristics.

## Heuristics in the TimeoutPolicy
The default timeout policy is fixed timeout, which means you'll always get the initial value in regular mode and the debug value in debug mode. But the basic premise of having a timeout policy is providing heuristics-based timeouts. Following are the main concepts used in the timeout policy.

In statistics, the [**68–95–99.7  rule**](http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule), also known as the **three-sigma rule** or **empirical rule**, states that nearly all values lie within three standard deviations of the mean in a normal distribution.
![empirical rule](http://upload.wikimedia.org/wikipedia/commons/a/a9/Empirical_Rule.PNG)
Therefore if you declare your timeout policy like below:

```scala
val policy = TimeoutPolicy("mypolicy", initial = 1 second, rule = 2 sigma)
```

You'll get a timeout value which will cover about 95% of the response times and cutting off the 5% anomalies by using a timeout policy of 2 sigma or 95 percentile. This is applied by calling `policy.execute(tiemout=>T)` or `policy.transaction.waitTime`.

### Reset the statistic
There are three ways to reset/start over the statistics

1. Set the `startOverCount` on constructing the TimeoutPolicy, which will start over the statistic automatically when the total transaction count exceed the `startOverCount`
2. Call `policy.reset` to reset the statistics, you can also give new `initial` and `startOverCount` on calling the reset method.
3. Call `TimeoutPolicy.resetPolicy("yourName")` to reset the policy on global level.

## The Name

Any construction of a timeout policy takes an optional name. Timeout policies share their metrics with other policy instances using the same name. A policy-by-name would prevent users from having to create an instance of the policy and pass it out to all usages sharing the same policies. Users can then cleanly replicate the policy creation at any point of use while still collecting the metrics together. In addition, using a name in the policy allows for a centralized clearing of the metrics by calling `TimeoutPolicy.resetPolicy("name")`

**Caution**: Do not use the same name for policies of totally different nature as that could mess up your stats. The results may not be predictable.

## Debugging
For debugging purposes, the default timeout in a timeout policy is 1,000 seconds when you are executing in debug mode. You can set this by passing a `debug` parameter into the TimeoutPolicy as follows:

```scala
val policy = TimeoutPolicy(name = Some("mypolicy"), initial = 1 second, debug = 10000 seconds)
```

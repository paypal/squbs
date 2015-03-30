#TimeoutPolicy
The TimeoutPolicy was designed for two purpose:
1. Use different timeout value if you're in debug mode.
2. Provide a heuristics-based timeout strategy.

##Quick example
```
Await.ready(future, timeout.duration)
```
Should be changed to:
```
val policy = TimeoutPolicy(name = Some("mypolicy"), initial = 1 second)
val tx = policy.transaction
Await.ready(future, tx.waitTime)
tx.end
```
or
```
val policy = TimeoutPolicy(name = Some("mypolicy"), initial = 1 second)
val result = policy.execute(duration => {
  Await.ready(future, duration)
})
```
Then you can get different timeout when you're in debug mode(which is 1000 seconds by default).

##Dependencies

Add the following dependency to your build.sbt or scala build file:

"org.squbs" %% "squbs-timeoutpolicy" % squbsVersion

##Heuristics-based TimeoutPolicy
The default timeout policy is fixed, which means you'll always get the initial value in regular mode and the debug value in debug mode, while we also provided a heuristics-based timeout policy.

In statistics, the [**68–95–99.7  rule**](http://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule), also known as the **three-sigma rule** or **empirical rule**, states that nearly all values lie within three standard deviations of the mean in a normal distribution.
![empirical rule](http://upload.wikimedia.org/wikipedia/commons/a/a9/Empirical_Rule.PNG)
Therefore if you declare your timeout policy like below:
```
val policy = TimeoutPolicy("mypolicy", 1 seconds, 2 sigma)
```
You'll get a timeout value which can cover about 95% cases on calling `policy.execute(tiemout=>T)` or `policy.transaction.waitTime` finally(2 sigma ≈ 95.45%).

### Reset the statistic
There are three ways to reset/start over the statistics

1. Set the `startOverCount` on constructing the TimeoutPolicy, which will start over the statistic automatically when the total transaction count exceed the `startOverCount`
2. Call `policy.reset` to reset the statistics, you can also give new `initial` and `startOverCount` on calling the reset method.
3. Call `TimeoutPolicy.resetPolicy("yourName")` to reset the policy on global level.

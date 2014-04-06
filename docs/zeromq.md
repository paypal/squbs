#Integration with `ZeroMQ`

Our integration with `ZeroMQ` is mainly based on [`jeromq`](https://github.com/zeromq/jeromq), the integration makes communications between akka & nearly all runtime possible.

## Some background
To support use case like [`higgins`](https://github.scm.corp.ebay.com/Higgins/proxy), we need to let message flow between `PHP`, `node.js`, `GO` and our `Akka` based proxy layer, the efficiency and wide support of runtime drives us to `ZeroMQ`.

## Value Proposition
We hit problems immediately after:
* `ZeroMQ`'s scala binding is terrible, both in terms of binary libzmq dependency and its slowness
* `ZeroMQ`'s send/receive behavior is nothing like `Akka`'s actor
* `ZeroMQ`'s various socket types demand different payload layouts, and it's on the user to learn and handle
 
To address various problems above, and make it simple enough to talk between Akka's Actor & various runtimes, we provided `ZSocketOnAkka` pattern from `squbs`

## Quick Start
Obviously you'll need the `squbs` dependencies in your `build.sbt` or `build.scala`
```scala
//resolvers
resolvers += "eBay Central QA" at "http://ebaycentral.qa.ebay.com/content/repositories/snapshots"

//libraryDependencies
"org.squbs"             %% "unicomplex"       % "0.3.0-SNAPSHOT" //to be updated
```
You could find most samples from: [`ZSocketActorSpec`](../unicomplex/src/test/scala/org/squbs/pattern/ZSocketActorSpec.scala)
And we'll go through a few of them to illustrate how it's done.
### pub/sub
#### create _subscribe_ actor 
```scala
val subActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.SUB, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
  self ! "got it"
}), None, None)))

subActor ! Identity("zmq-sub")
subActor ! Connect(s"tcp://127.0.0.1:$port")
subActor ! Seq(ByteString("zmq-topic"))
```
#### create _publish_ actor
```scala
val pubActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PUB, None, None, None)))

pubActor ! Identity("zmq-pub")
pubActor ! Bind(s"tcp://127.0.0.1:$port")
pubActor ! Seq(ByteString("zmq-topic"), ByteString("some content"))
```
#### notes
* We use `ZSocketOnAkka` companion object to create actors that stand for ZeroMQ sockets
* We have socketType:`Int`, incomingOption:`Option[(ZEnvelop,ActorContext)=>Unit]`, outgoingOption:`Option[(ZEnvelop,Socket)=>Unit]`, unknownOption:`Option[(Any,Socket)=>Unit]`
* socketType could be any of \[`ZMQ.REQ`, `ZMQ.REP`, `ZMQ.PAIR`, `ZMQ.ROUTER`, `ZMQ.DEALER`, `ZMQ.PUB`, `ZMQ.SUB`, `ZMQ.PUSH`, `ZMQ.PULL`\]
* incomingOption could be `None`, or `Some` function that handles `ZEnvelop` and `ActorContext` coming in from ZeroMQ socket
* outgoingOption could be `None`, or `Some` function that handles `ZEnvelop` and `Socket` going out to ZeroMQ socket
* To get a socket actor to run, there's some configurations needed in the list of \[`Identity`, `ReceiveHWM`, `SendHWM`, `MaxMessageSize`, `MaxDelay`, `Connect`, `Bind`\]
* Each of the configurable aspect above could be sent to the actor via their typed message
* Once `Bind` message is received, the actor will start listening on the appointed address
* Once `Connect` message is received, the actor will start connecting to the given address, and start listening once connected
* Neither of `Bind`, `Connect` could be sent more than once, and `Identity` is mandatory for at least once
* Internally, our `ZSocketOnAkka` actor is built upon `FSM` which allows state transitions to reflect the `Bind`, `Connect` state
* Afterwards, our actors could handle `ZEnvelop` typed message, in case of subscribe above, for example, we sent `Seq(ByteString("zmq-topic"))` to the actor
* In particular, `Seq(ByteString("zmq-topic"))` is interpreted as a `subscribe` command to be sent to publish ZeroMQ socket
* Publish actor which accepts `Seq(ByteString("zmq-topic"), ByteString("some content"))` takes 1st chunk as `topic`, and publish the rest of payload to all subscriptions

### req/res
#### create _req_ actor
```scala
val reqActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REQ, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
  self ! "got it"
}), None, None)))

reqActor ! Identity("zmq-req")
reqActor ! Bind(s"tcp://127.0.0.1:$port")
reqActor ! Seq(ByteString("some request"))
```
#### create _rep_ actor
```scala
val repActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REP, None, None, None)))

repActor ! Identity("zmq-rep")
repActor ! Connect(s"tcp://127.0.0.1:$port")
```
#### notes
* Not much differences from pub/sub, and that's exactly what we wanted, there're `push/pull`, `pair/pair`, `router/dealer` in additions, we don't want you to learn everyone differently
* In particular, you send `Seq(ByteString("some request"))` to our request actor, who sends via the ZeroMQ REQ socket

## Contribution
* We'd love to have your support on ZeroMQ integration, esp. if you're a `PHP`, `Python` developer who needs to talk with `Akka` for some reason
* Issue JIRA's or just file ticket under https://github.scm.corp.ebay.com/Squbs/squbs/issues
* We've yet seen a need to support ZeroMQ devices, like `Queue`, but let us know if you do.

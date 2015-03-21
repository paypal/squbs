#Orchestration DSL

Orchestration is one of the major use cases for services, whether you try to orchestrate multiple service calls with as much concurrency, and therefore as good a response time, as possible, or you try to do multiple business operations, data writes, data reads, service calls, etc. dependent on each others, etc. The ability to concisely describe your business logic concisely is essential to making the service easy to understand and maintain. The orchestration DSL, part of squbs-pattern, will make your life easy and make asynchronous code easy to reason about.

##Core Concepts

###Orchestrator

`Orchestrator` is a trait extended by actors to support the orchestration functionality. It is technically a child trait of the [Aggregator](http://doc.akka.io/docs/akka/snapshot/contrib/aggregator.html) and provides all its functionality. In addition, it provides further functionality allowing effective orchestration composition, plus some utilities to allow easy creation of orchestration functions we will discuss in detail below. To use the orchestrator, an actor would extend the `Orchestrator` trait.

```scala
org.squbs.pattern.orchestration.Orchestrator

class MyOrchestrator extends Actor with Orchestrator {
  ...
}
```

Similar to Aggregator, an orchestrator generally does not declare the Akka actor receive block but allows the expect/expectOnce/unexpect blocks to define what responses are expected at any point in time. These expect blocks are generally used from inside orchestration functions.

###Orchestration Future and Promise

The orchestration promise and future is very similar to [scala.concurrent.Future](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Future) and [scala.concurrent.Promise](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Promise$) described with samples [here](http://docs.scala-lang.org/overviews/core/futures.html) with only a name change to OFuture and OPromise, signifying them to be used with the Orchestrator, inside an actor. The main difference between the concurrent version and the orchestration version of the artifacts is that the orchestration version is optimized for use inside an actor. They are non-concurrent and do not use an (implicit) execution context in their signatures, and for execution. They also lack a few functions explicitly telling an actor to execute. Used inside an actor, their callbacks will never be called outside the scope of an actor. This will eliminate the danger of concurrently modifying actor state from a different thread due to callbacks. Furthermore, their callback mechanism is highly optimized for in-actor usage making them the ideal candidate for orchestration.

```scala
import org.squbs.pattern.orchestration.{OFuture, OPromise} 
```

###Asynchronous Orchestration Functions

Orchestration functions are the functions that the orchestrator calls to execute single orchestration tasks, such as making a service or database call. An orchestration function must comply to the following guideline:

1. It must take non Future arguments as input. Based on current limitations in the language, the number of arguments can be up to 22. But if you have a function with 22 arguments, there is likely something wrong with the way you design it.
2. It may be [curried](http://docs.scala-lang.org/tutorials/tour/currying.html) to separate direct input from piped (future) input. The piped input must be the last set of arguments in a curried function. 
3. It must cause asynchronous execution. The asynchronous execution is generally achieved by sending a message to be processed by a different actor.
4. It must return an OFuture (orchestration future).

Lets look at a few examples of orchestration functions:

```scala
def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  val itemPromise = OPromise[Option[Item]]
  
  context.actorOf[ItemActor] ! ItemRequest(itemId, seller.id)
  
  expectOnce {
    case item: Item    => itemPromise success Some(item)
    case e: NoSuchItem => itemPromise success None
  }
  
  itemPromise.future
}
```

In this example, the function is curried. The `itemId` argument is delivered synchronously and the seller is delivered asynchronously. We start the function with creating an OPromise to hold the eventual value. Then we make an ItemRequest by sending it to another actor. This actor will now asynchronously obtain the item. Once we have sent the request, we register a callback with expectOnce. The code inside the expectOnce is a partial function that will execute based on the ItemActor sending the response back. In all cases, it will `success` the promise. At the end, we send out the future associated with the promise. The reason for not returning a promise is the fact it is mutable. We do not want to return a mutable object out of the function. Calling `future` on it will provide an immutable view of the `OPromise`, which is the `OFuture`.

The example below is logically the same as the first example, just using ask instead of tell:

```scala
def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  
  import context.dispatcher
  implicit val timeout = Timeout(3 seconds)
  
  context.actorOf[ItemActor] ? ItemRequest(itemId, seller.id)
}
```

In this case, the ask `?` operation returns a [scala.concurrent.Future](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Future). The Orchestrator trait provides implicit conversions between scala.concurrent.Future and OFuture so the result of the ask `?` can be returned from this function declaring an OFuture as a return type without explicitly calling a conversion.

While ask `?` may seem to require writing less code, it is both less performant and less flexible as expect/expectOnce. The logic in the expect block could as well be used for further transformations of the result. The same can be achieved by using callbacks on the future returned from ask. The performance, however, cannot be easily compensated for, for the following reasons:

1. Ask will create a new actor as a receiver of the response.
2. The conversion from [scala.concurrent.Future](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Future) to OFuture will need a message to be piped back to the orchestrator, thus adding a message hop adding both latency and CPU.

Tests have shown sligtly higher latency as well as CPU utilizations when using ask as opposed to expect/expectOnce.

###The Pipe

The pipe, or `>>` symbol takes one or more orchestration future `OFuture` and makes its outcome as the input to the orchestration function. The actual orchestration function invocation will happen asynchronously, when all the `OFuture`s representing the input to the function are resolved.

The pipe is the main component of the Orchestration DSL allowing orchestration functions to be composed based on their input and output. The orchestration flow is determined implicitly by the orchestration declaration, or the declared flow of the orchestration, through the pipes.

When multiple OFutures are piped to an orchestration function, the OFutures need to be comma-separated and enclosed in parentheses, constructing a tuple of OFutures as input. The number of elements in the tuple their OFuture types must match the function arguments and types, or the last set of arguments in case of a [curry](http://docs.scala-lang.org/tutorials/tour/currying.html) or the compilation will fail. Such errors are normally also cought by the IDE. The following example shows a simple orchestration declaration and flow using the loadItem orchestration function declared above, in previous sections:

```scala
val userF = loadViewingUser
val itemF = userF >> loadItem(itemId)
val itemViewF = (userF, itemF) >> buildItemView
```

The flow above can be described as follows:

* First call loadViewingUser (with no input).
* When the viewing user becomes available, use the viewing user as an input to call loadItem (in this case with an itemId available in prior). loadItem in this case follows the exact signature in the orchestration function declaration above.
* When both user and item becomes available, call buildItemView.

##Orchestrator Instance Lifecycle

Orchestrators are generally single-use actors. The receive the initial request and then multiple responses based on the invoked orchestration functions. To allow an orchestrator to serve multiple orchestration requests, the orchestrator would have to combine the input and responses for each request and segregate them from different requests. This will largely complicate its development and will likely not end up in a clear orchestration declaration we see these examples. Creating new actors are cheap enough we could easily create a new orchestrator for each orchestration request.

The last part of an orchestration callback shall definitely stop the actor. This is done by calling `context.stop(self)` or `context stop self` if the infix notation is preferred.

##Orchestration Code Flow

Generally, the code flow of the orchestrator is as follows:

```scala
    // 1. Declare the orchestrator actor.
class MyOrchestrator extends Actor with Orchestrator {

    // 2. Provide the initial expectOnce block that will receive the request message.
    //    After this request message is received, the same request will not be
    //    expected again for the same actor.
    //    The expectOnce only has one case which is the initial request and uses
    //    the request arguments or members, and the sender() to call the high
    //    level orchestration function. This function is usually named orchestrate.
  expectOnce {
    case r: MyOrchestrationRequest => orchestrate(sender(), r)
  }
  
    // 3. Define orchestrate. Its arguments become immutable by default
    //    allowing developers to rely on the fact these will never change.
  def orchestrate(requester: ActorRef, request: MyOrchestrationRequest) {
  
    // 4. If there is anything we need to do synchronously to setup for
    //    the orchestration, do this in the first part of orchestrate.
  
    // 5. Compose the orchestration flow using pipes as needed by the business logic.
    val userF = loadViewingUser
    val itemF = userF >> loadItem(itemId)
    val itemViewF = (userF, itemF) >> buildItemView
    
    // 6. End the flow by calling functions composing the response(s) back to the
    //    requester. If the composition is very large, it may be more readable to
    //    use for-comprehensions rather than a composition function with very large
    //    number of arguments. There may be multiple such compositions in case partial
    //    responses are desired. This example shows the use of a for-comprehension
    //    just for reference. You can also use an orchestration function with
    //    3 arguments plus the requester in such small cases.
    
    for {
      user <- userF
      item <- itemF
      itemView <- itemViewF
    } {
      requester ! OrchestrationResult(user, item, itemView)
      context.stop(self)
    }
    
    // 7. Make sure the last response stops the orchestrator actor.
  }
  
    // 8. Implement the asynchronous orchestration functions inside the
    //    orchestrator actor, but outside the orchestrate function.
  def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
    val itemPromise = OPromise[Option[Item]]
  
    context.actorOf[ItemActor] ! ItemRequest(itemId, seller.id)
  
    expectOnce {
      case item: Item    => itemPromise success Some(item)
      case e: NoSuchItem => itemPromise success None
    }
  
    itemPromise.future
  }
  
  def loadViewingUser: OFuture[Option[User]] = {
    val userPromise = OPromise[Option[User]]
    ...
    userPromise.future
  }
  
  def buildItemView: OFuture[Option[ItemView]] = {
    ...
  }
}
```

##Re-use of Orchestration Functions

Orchestration functions often rely on the facilities provided by the `Orchestrator` trait and cannot live stand-alone. However, in many cases, re-use of the orchestration functions across multiple orchestrators are desired. In such cases it is important to separate the orchestration functions into a different trait(s) that will be mixed into each of the orchestrators. The following shows a sample of such a trait:

```scala
trait OrchestrationFunctions { this: Actor with Orchestrator =>

  def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
    ...
  }
}
```

The `this: Actor with Orchestrator` in the sample above is a typed self reference. It tells the Scala compiler that this trait can only be mixed into an `Actor` that is also an `Orchestrator` and therefore will have access to the facilities provided by both `Actor` and `Orchestrator`, using these facilities from the traits it got mixed into.

To use the OrchestrationFunctions inside an orchestrator, one would just mix this trait into an orchestrator as follows:

```scala
class MyOrchestrator extends Actor with Orchestrator with OrchestrationFunctions {
  ...
}
```

##Ensuring Response Uniqueness

When using `expect` or `expectOnce`, we're limited by the pattern match capabilities of a single expect block which is limited in scope and cannot distinguish between matches across multiple expect blocks in multiple orchestration functions. There is no logical link that the received message is from the request message sent just before declaring the expect, in the same orchestration function. For complicated orchestrations, we may run into issues of message confusion. The response is not associated with the right request. There are a few strategies for dealing with this problem:

If the recipient of the initial message, and therefore the sender of the response message is unique, the match could include a reference to the message's sender as in the sample pattern match below.

```scala
def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  val itemPromise = OPromise[Option[Item]]
  
  val itemActor = context.actorOf[ItemActor]  
  itemActor ! ItemRequest(itemId, seller.id)
  
  expectOnce {
    case item: Item    if sender() == itemActor => itemPromise success Some(item)
    case e: NoSuchItem if sender() == itemActor => itemPromise success None
  }
  
  itemPromise.future
}
```

Alternatively, the `Orchestrator` trait provides a message id generator that is unique when combined with the actor instance. We can use this id generator to generate a unique message id. Actors that accept such messages will just need to return this message id as part of the response message. The sample below shows an orchestration function using the message id generator.

```scala
def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  val itemPromise = OPromise[Option[Item]]
  
  val msgId = nextMessageId  
  context.actorOf[ItemActor] ! ItemRequest(msgId, itemId, seller.id)
  
  expectOnce {
    case item @ Item(`msgId`, _, _) => itemPromise success Some(item)
    case NoSuchItem(`msgId`, _)     => itemPromise success None
  }
  
  itemPromise.future
}
```






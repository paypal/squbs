# The Orchestration DSL

**Important Note:** _The Orchestration DSL is deprecated and will be removed in a future version.
Orchestration can be better achieved using pekko Streams_

Orchestration is one of the major use cases for services, whether you try to orchestrate multiple service calls with as much concurrency, and therefore as good a response time, as possible, or you try to do multiple business operations, data writes, data reads, service calls, etc. dependent on each others, etc. The ability to concisely describe your business logic is essential to making the service easy to understand and maintain. The orchestration DSL - part of squbs-pattern - will make asynchronous code easy to write, read, and reason about.

## Dependencies

The Orchestration DSL is part of `squbs-pattern`. To use the Orchestration DSL, please add the following dependency:

```scala
"org.squbs" %% "squbs-pattern" % squbsVersion
```

## Getting Started
Lets get started with a simple, but complete example of orchestration. This orchestrator composes 3 interrelated asynchronous tasks:

1. Loading the viewing user requesting this orchestration.
2. Loading the item. Details may depend on the viewing user.
3. Build an item view based on the user and item data.

Lets dive into the flow and detail.

#### Scala

```scala
    // 1. Define the orchestrator actor.
class MyOrchestrator extends Actor with Orchestrator {

    // 2. Provide the initial expectOnce block that will receive the request message.
  expectOnce {
    case r: MyOrchestrationRequest => orchestrate(sender(), r)
  }
  
    // 3. Define orchestrate - the orchestration function.
  def orchestrate(requester: ActorRef, request: MyOrchestrationRequest) {
    
    // 4. Compose the orchestration flow using pipes (>>) as needed by the business logic.
    val userF = loadViewingUser
    val itemF = userF >> loadItem(request.itemId)
    val itemViewF = (userF, itemF) >> buildItemView
    
    // 5. Conclude and send back the result of the orchestration.    
    for {
      user <- userF
      item <- itemF
      itemView <- itemViewF
    } {
      requester ! MyOrchestrationResult(user, item, itemView)
      context.stop(self)
    }
    
    // 6. Make sure to stop the orchestrator actor by calling
    //    context.stop(self).
  }
  
    // 7. Implement the orchestration functions as in the following patterns.
  def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
    val itemPromise = OPromise[Option[Item]]
  
    context.actorOf(Props[ItemActor]) ! ItemRequest(itemId, seller.id)
  
    expectOnce {
      case item: Item => itemPromise success Some(item)
      case e: NoSuchItem => itemPromise success None
    }
  
    itemPromise.future
  }
  
  def loadViewingUser: OFuture[Option[User]] = {
    val userPromise = OPromise[Option[User]]
    ...
    userPromise.future
  }
  
  def buildItemView(user: Option[User], item: Option[Item]): OFuture[Option[ItemView]] = {
    ...
  }
}
```

#### Java
Due to language limitations in Java, the same implementation looks quite a bit more verbose:

```java
    // 1. Define the orchestrator actor.
public class MyOrchestrator extends AbstractOrchestrator {

    // 2. Provide the initial expectOnce in the constructor. It will receive the request message.
    public MyOrchestrator() {
        expectOnce(ReceiveBuilder.match(MyOrchestrationRequest.class, r -> orchestrate(r, sender())).build());
    }
  
    // 3. Define orchestrate - the orchestration function.
    public void orchestrate(MyOrchestrationRequest request, ActorRef requester) {
    
        // 4. Compose the orchestration flow as needed by the business logic.
        static CompletableFuture<Optional<User>> userF = loadViewingUser();
        static CompletableFuture<Optional<Item>> itemF = 
            userF.thenCompose(user -> 
                loadItem(user, request.itemId));
        static CompletableFuture<Optional<ItemView>> itemViewF =
            userF.thenCompose(user ->
            itemF.thenCompose(item ->
                buildItemView(user, item)));
    
        // 5. Conclude and send back the result of the orchestration. 
        userF.thenCompose(user ->
        itemF.thenCompose(item ->
        itemViewF.thenAccept(itemView -> {
            requester.tell(new MyOrchestrationResult(user, item, itemView), self());
            context().stop(self());
        })));
    
        // 6. Make sure to stop the orchestrator actor by calling
        //    context.stop(self).
    }
  
    // 7. Implement the orchestration functions as in the following patterns.
    private CompletableFuture<Optional<Item>> loadItem(User seller, String itemId) {
        CompletableFuture<Optional<Item>> itemF = new CompletableFuture<>();
        context().actorOf(Props.create(ItemActor.class))
            .tell(new ItemRequest(itemId, seller.id), self());
        expectOnce(ReceiveBuilder.
            match(Item.class, item ->
                itemF.complete(Optional.of(item)).
            matchEquals(noSuchItem, e ->
                itemF.complete(Optional.empty())).
            build()
        );
        return itemF;
    }
    
    private CompletableFuture<Optional<User>> loadViewingUser() {
        CompletableFuture<Optional<User>> userF = new CompletableFuture<>();
        ...
        return userF.future
    }
    
    private CompletableFuture<Optional<ItemView>> buildItemView(Optional<User> user, Optional<Item> item) {
        ...
    }
}
```

You may stop here and come back reading the rest later for a deep dive. Feel free to go further and satisfy your curious minds, though.

## Dependencies

Add the following dependency to your build.sbt or scala build file:

```
"org.squbs" %% "squbs-pattern" % squbsVersion
```

## Core Concepts

### Orchestrator

`Orchestrator` is a trait extended by actors to support the orchestration functionality. It is technically a child trait of the [Aggregator](http://doc.pekko.io/docs/pekko/snapshot/contrib/aggregator.html) and provides all its functionality. In addition, it provides functionality and syntax allowing effective orchestration composition - plus utilities often needed in the creation of orchestration functions discussed in detail below. To use the orchestrator, an actor would simply extend the `Orchestrator` trait.

```scala
import org.squbs.pattern.orchestration.Orchestrator

class MyOrchestrator extends Actor with Orchestrator {
  ...
}
```

The `AbstractOrchestrator` is the Java superclass for Java users. It composes Actor and Orchestrator under the cover as Java does not support trait mix-ins. So the Java orchestrator will be written out as follows:

```java
import org.squbs.pattern.orchestration.japi.AbstractOrchestrator;

public class MyOrchestrator extends AbstractOrchestrator {
    ...
}
```

Similar to Aggregator, an orchestrator generally does not declare the pekko actor receive block but allows the expect/expectOnce/unexpect blocks to define what responses are expected at any point. These expect blocks are generally used from inside orchestration functions.

### Scala: Orchestration Future and Promise

The orchestration promise and future is very similar to [`scala.concurrent.Future`](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Future) and [`scala.concurrent.Promise`](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Promise$) described [here](http://docs.scala-lang.org/overviews/core/futures.html) with only a name change to `OFuture` and `OPromise`, signifying them to be used with the Orchestrator, inside an actor. The orchestration versions differentiate themselves from the concurrent versions of the artifacts by having no concurrency behavior. They do not use an (implicit) `ExecutionContext` in their signatures, and for execution. They also lack a few functions explicitly executing a closure asynchronously. Used inside an actor, their callbacks will never be called outside the scope of an actor. This will eliminate the danger of concurrently modifying actor state from a different thread due to callbacks. In addition, they include performance optimizations assuming they will always be used inside an actor.

**Note:** DO NOT pass an `OFuture` outside an actor. Implicit conversions are provided to convert between `scala.concurrent.Future` and `OFuture`.

```scala
import org.squbs.pattern.orchestration.{OFuture, OPromise} 
```

### Java: `CompletableFuture`

The java CompletableFuture with its **synchronous** callbacks are used as placeholders for values to be satisfied during the orchestration. The synchronous callbacks ensures the processing of the future to happen on the thread it is completed. In the orchestration model, it would be the thread the orchestrator actor is scheduled for receiving and processing the message. This ensures there is no concurrent close-over of the actor state. All callbacks are processed in the scope of the actor.

```java
import java.util.concurrent.CompletableFuture;
```

### Asynchronous Orchestration Functions

Orchestration functions are the functions called by the orchestration flow to execute single orchestration tasks, such as making a service or database call. An orchestration function must comply to the following guideline:

1. It must take non Future arguments as input. Based on current limitations in Scala, the number of arguments can be up to 22. The Java-style orchestration does not expose this limitation. In all cases, such functions should not have that many parameters.

2. Scala functions may be [curried](http://docs.scala-lang.org/tutorials/tour/currying.html) to separate direct input from piped (future) input. The piped input must be the last set of arguments in a curried function. 
3. It must cause asynchronous execution. The asynchronous execution is generally achieved by sending a message to be processed by a different actor.
4. Scala implementations must return an OFuture (orchestration future). Java implementations must return a CompletableFuture.

Lets look at a few examples of orchestration functions:

#### Scala

```scala
def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  val itemPromise = OPromise[Option[Item]]
  
  context.actorOf(Props[ItemActor]) ! ItemRequest(itemId, seller.id)
  
  expectOnce {
    case item: Item => itemPromise success Some(item)
    case e: NoSuchItem => itemPromise success None
  }
  
  itemPromise.future
}
```

In this example, the function is curried. The `itemId` argument is delivered synchronously and the seller is delivered asynchronously.

#### Java

```java
private CompletableFuture<Optional<Item>> loadItem(User seller, String itemId) {
    CompletableFuture<Optional<Item>> itemF = new CompletableFuture<>();
    context().actorOf(Props.create(ItemActor.class))
        .tell(new ItemRequest(itemId, seller.id), self());
    expectOnce(ReceiveBuilder.
        match(Item.class, item ->
            itemF.complete(Optional.of(item)).
        matchEquals(noSuchItem, e ->
            itemF.complete(Optional.empty())).
        build()
    );
    return itemF;
}
```

We start the function with creating an `OPromise` (Scala) or `CompletableFuture` (Java) to hold the eventual value. Then we make an ItemRequest by sending it to another actor. This actor will now asynchronously obtain the item. Once we have sent the request, we register a callback with expectOnce. The code inside the expectOnce is a `Receive` that will execute based on the ItemActor sending the response back. In all cases, it will `success` the promise or `complete` the CompletableFuture. At the end, we send out the future. The reason for not returning a promise is the fact it is mutable. We do not want to return a mutable object out of the function. Calling `future` on it will provide an immutable view of the `OPromise`, which is the `OFuture`. Unfortunately, this cannot apply to Java.

The example below is logically the same as the first example, just using ask instead of tell:

#### Scala

```scala
private def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  
  import context.dispatcher
  implicit val timeout = Timeout(3 seconds)
  
  (context.actorOf[ItemActor] ? ItemRequest(itemId, seller.id)).mapTo[Option[Item]]
}
```

In this case, the ask `?` operation returns a [`scala.concurrent.Future`](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Future). The Orchestrator trait provides implicit conversions between scala.concurrent.Future and OFuture so the result of the ask `?` can be returned from this function declaring an OFuture as a return type without explicitly calling a conversion.

#### Java

```java
private CompletableFuture<Optional<Item>> loadItem(User seller, String itemId) {
    CompletableFuture<Optional<Item>> itemF = new CompletableFuture<>();
    Timeout timeout = new Timeout(Duration.create(5, "seconds"));
    ActorRef itemActor = context().actorOf(Props.create(ItemActor.class));
    ask(itemActor, new ItemRequest(itemId, seller.id), timeout).thenComplete(itemF);
    return itemF;
}
```

The `AbstractOrchestrator` for provides convenience functions for `ask `that allows filling the result straight into a `CompletableFuture` using the `thenComplete` operation.

While `ask` or `?` may seem to require writing less code, it is both less performant and less flexible as expect/expectOnce. The logic in the expect block could as well be used for further transformations of the result. The same can be achieved by using callbacks on the future returned from ask. The performance, however, cannot be easily compensated for, for the following reasons:

1. Ask will create a new actor as a receiver of the response.
2. The conversion from [`scala.concurrent.Future`](http://www.scala-lang.org/api/2.11.4/index.html#scala.concurrent.Future) to `OFuture` as well as the `fill` operation for Java API will need a message to be piped back to the orchestrator, thus adding a message hop adding both latency and CPU.

Tests have shown sligtly higher latency as well as CPU utilizations when using ask as opposed to expect/expectOnce.

### Composition

#### Scala

The pipe, or `>>` symbol takes one or more orchestration future `OFuture` and makes its outcome as the input to the orchestration function. The actual orchestration function invocation will happen asynchronously, when all the `OFuture`s representing the input to the function are resolved.

The pipe is the main component of the Orchestration DSL allowing orchestration functions to be composed based on their input and output. The orchestration flow is determined implicitly by the orchestration declaration, or the declared flow of the orchestration through piping.

When multiple OFutures are piped to an orchestration function, the OFutures need to be comma-separated and enclosed in parentheses, constructing a tuple of OFutures as input. The number of elements in the tuple their OFuture types must match the function arguments and types, or the last set of arguments in case of a [curry](http://docs.scala-lang.org/tutorials/tour/currying.html), or the compilation will fail. Such errors are normally also cought by the IDE.

The following example shows a simple orchestration declaration and flow using the loadItem orchestration function declared in previous sections, amongst others:

```scala
val userF = loadViewingUser
val itemF = userF >> loadItem(request.itemId)
val itemViewF = (userF, itemF) >> buildItemView
```

The flow above can be described as follows:

* First call loadViewingUser (with no input).
* When the viewing user becomes available, use the viewing user as an input to call loadItem (in this case with an itemId available in prior). loadItem in this case follows the exact signature in the orchestration function declaration above.
* When both user and item becomes available, call buildItemView.

#### Java

The composition of multiple `CompletableFuture`s can be achieved by using composition function `CompletableFuture.thenCompose()`. Each `thenCompose` takes a lambda with the resolved future value as input. This will be called when the `CompletableFuture` is completed.

It is best to describe such composition using an example:

```java
static CompletableFuture<Optional<User>> userF = loadViewingUser();
static CompletableFuture<Optional<Item>> itemF = 
    userF.thenCompose(user -> 
        loadItem(user, request.itemId));
static CompletableFuture<Optional<ItemView>> itemViewF =
    userF.thenCompose(user ->
    itemF.thenCompose(item ->
        buildItemView(user, item)));
```

The flow above can be described as follows:

* First call loadViewingUser.
* When the viewing user becomes available, use the viewing user as an input to call loadItem (in this case with an itemId available in prior).
* When both user and item becomes available, call buildItemView.

## Orchestrator Instance Lifecycle

Orchestrators are generally single-use actors. They receive the initial request and then multiple responses based on what requests the invoked orchestration functions send out.

To allow an orchestrator to serve multiple orchestration requests, the orchestrator would have to combine the input and responses for each request and segregate them from different requests. This will largely complicate its development and will likely not end up in a clear orchestration reprsentation we see in these examples. Creating a new actor is cheap enough we could easily create a new orchestrator actors for each orchestration request.

The last part of an orchestration callback should stop the actor. This is done in Scala by calling `context.stop(self)` or `context stop self` if the infix notation is preferred. Java implementations need to call `context().stop(self());`

## Complete Orchestration Flow

Here, we put all the above concepts together. Repeating the same example from Getting Started above with more complete explanations:

#### Scala

```scala
    // 1. Define the orchestrator actor.
class MyOrchestrator extends Actor with Orchestrator {

    // 2. Provide the initial expectOnce block that will receive the request message.
    //    After this request message is received, the same request will not be
    //    expected again for the same actor.
    //    The expectOnce likely has one case match which is the initial request and
    //    uses the request arguments or members, and the sender() to call the high
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
    val itemF = userF >> loadItem(request.itemId)
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
      requester ! MyOrchestrationResult(user, item, itemView)
      context.stop(self)
    }
    
    // 7. Make sure the last response stops the orchestrator actor by calling
    //    context.stop(self).
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
  
  def buildItemView(user: Option[User], item: Option[Item]): OFuture[Option[ItemView]] = {
    ...
  }
}
```

#### Java

```java
    // 1. Define the orchestrator actor.
public class MyOrchestrator extends AbstractOrchestrator {

    // 2. Provide the initial expectOnce in the constructor. It will receive the request message.
    public MyOrchestrator() {
        expectOnce(ReceiveBuilder.match(MyOrchestrationRequest.class, r -> orchestrate(r, sender())).build());
    }
  
    // 3. Define orchestrate - the orchestration function.
    public void orchestrate(MyOrchestrationRequest request, ActorRef requester) {

        // 4. If there is anything we need to do synchronously to setup for
        //    the orchestration, do this in the first part of orchestrate.
  
        // 5. Compose the orchestration flow as needed by the business logic.
        static CompletableFuture<Optional<User>> userF = loadViewingUser();
        static CompletableFuture<Optional<Item>> itemF = 
            userF.thenCompose(user -> 
                loadItem(user, request.itemId));
        static CompletableFuture<Optional<ItemView>> itemViewF =
            userF.thenCompose(user ->
            itemF.thenCompose(item ->
                buildItemView(user, item)));
    
        // 6. Conclude and send back the result of the orchestration. 
        userF.thenCompose(user ->
        itemF.thenCompose(item ->
        itemViewF.thenAccept(itemView -> {
            requester.tell(new MyOrchestrationResult(user, item, itemView), self());
            context().stop(self());
        })));
    
        // 7. Make sure to stop the orchestrator actor by calling
        //    context.stop(self).
    }
  
    // 8. Implement the orchestration functions as in the following patterns.
    private CompletableFuture<Optional<Item>> loadItem(User seller, String itemId) {
        CompletableFuture<Optional<Item>> itemF = new CompletableFuture<>();
        context().actorOf(Props.create(ItemActor.class))
            .tell(new ItemRequest(itemId, seller.id), self());
        expectOnce(ReceiveBuilder.
            match(Item.class, item ->
                itemF.complete(Optional.of(item)).
            matchEquals(noSuchItem, e ->
                itemF.complete(Optional.empty())).
            build()
        );
        return itemF;
    }
    
    private CompletableFuture<Optional<User>> loadViewingUser() {
        CompletableFuture<Optional<User>> userF = new CompletableFuture<>();
        ...
        return userF;
    }
    
    private CompletableFuture<Optional<ItemView>> buildItemView(Optional<User> user, Optional<Item> item) {
        ...
    }
}
```

## Re-use of Orchestration Functions

#### Scala

Orchestration functions often rely on the facilities provided by the `Orchestrator` trait and cannot live stand-alone. However, in many cases, re-use of the orchestration functions across multiple orchestrators that orchestrate differently are desired. In such cases it is important to separate the orchestration functions into different trait(s) that will be mixed into each of the orchestrators. The trait has to have access to orchestration functionality and needs a self reference to the `Orchestrator`. The following shows a sample of such a trait:

```scala
trait OrchestrationFunctions { this: Actor with Orchestrator =>

  def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
    ...
  }
}
```

The `this: Actor with Orchestrator` in the sample above is a typed self reference. It tells the Scala compiler that this trait can only be mixed into an `Actor` that is also an `Orchestrator` and therefore will have access to the facilities provided by both `Actor` and `Orchestrator`, using these facilities from the traits and classes it got mixed into.

To use the `OrchestrationFunctions` trait inside an orchestrator, one would just mix this trait into an orchestrator as follows:

```scala
class MyOrchestrator extends Actor with Orchestrator with OrchestrationFunctions {
  ...
}
```

#### Java

Java requires a single hierarchy and cannot support traits or multiple inheritance. Re-use is achieved by extending the AbstractOrchestrator, implementing the orchestration functions, and leaving the rest abstract - to be implemented by the concrete implementation of the orchestrator as illustrated in the followings:

```java
abstract class MyAbstractOrchestrator extends AbstractOrchestrator {

    protected CompletableFuture<Optional<Item>> loadItem(User seller, String itemId) {
        CompletableFuture<Optional<Item>> itemF = new CompletableFuture<>();
        ...
        return itemF;
    }
    
    protected CompletableFuture<Optional<User>> loadViewingUser() {
        CompletableFuture<Optional<User>> userF = new CompletableFuture<>();
        ...
        return userF;
    }
    
    protected CompletableFuture<Optional<ItemView>> buildItemView(Optional<User> user, Optional<Item> item) {
        ...
    }
}
```

The concrete implementation of the orchestrator then just extends from `MyAbstractOrchestrator` above and implements the different orchestration.

## Ensuring Response Uniqueness

When using `expect` or `expectOnce`, we're limited by the pattern match capabilities of a single expect block which is limited in scope and cannot distinguish between matches across multiple expect blocks in multiple orchestration functions. There is no logical link that the received message is from the request message sent just before declaring the expect in the same orchestration function. For complicated orchestrations, we may run into issues of message confusion. The response is not associated with the right request and not processed correctly. There are a few strategies for dealing with this problem:

If the recipient of the initial message, and therefore the sender of the response message is unique, the match could include a reference to the message's sender as in the sample pattern match below.

#### Scala

```scala
def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  val itemPromise = OPromise[Option[Item]]
  
  val itemActor = context.actorOf(Props[ItemActor])
  itemActor ! ItemRequest(itemId, seller.id)
  
  expectOnce {
    case item: Item    if sender() == itemActor => itemPromise success Some(item)
    case e: NoSuchItem if sender() == itemActor => itemPromise success None
  }
  
  itemPromise.future
}
```

#### Java

```java
private CompletableFuture<Optional<Item>> loadItem(User seller, String itemId) {
    CompletableFuture<Optional<Item>> itemF = new CompletableFuture<>();
    ActorRef itemActor = context().actorOf(Props.create(ItemActor.class));
    itemActor.tell(new ItemRequest(itemId, seller.id), self());
    expectOnce(ReceiveBuilder.
        match(Item.class, item -> itemActor.equals(sender()), item ->
            itemF.complete(Optional.of(item)).
        matchEquals(noSuchItem, e -> itemActor.equals(sender()), e ->
            itemF.complete(Optional.empty())).
        build()
    );
    return itemF;
}
```

Alternatively, the `Orchestrator` trait provides a message id generator that is unique when combined with the actor instance. We can use this id generator to generate a unique message id. Actors that accept such messages will just need to return this message id as part of the response message. The sample below shows an orchestration function using the message id generator.

#### Scala

```scala
def loadItem(itemId: String)(seller: User): OFuture[Option[Item]] = {
  val itemPromise = OPromise[Option[Item]]
  
  // Generate the message id.
  val msgId = nextMessageId  
  context.actorOf(Props[ItemActor]) ! ItemRequest(msgId, itemId, seller.id)
  
  // Use the message id as part of the response pattern match. It needs to
  // be back-quoted as to not be interpreted as variable extractions, where
  // a new variable is created by extraction from the matched object.
  expectOnce {
    case item @ Item(`msgId`, _, _) => itemPromise success Some(item)
    case NoSuchItem(`msgId`, _)     => itemPromise success None
  }
  
  itemPromise.future
}
```

#### Java

```java
private CompletableFuture<Optional<Item>> loadItem(User seller, String itemId) {
    CompletableFuture<Optional<Item>> itemF = new CompletableFuture<>();
    long msgId = nextMessageId();
    context().actorOf(Props.create(ItemActor.class))
        .tell(new ItemRequest(msgId, itemId, seller.id), self());
    expectOnce(ReceiveBuilder.
        match(Item.class, item -> item.msgId == msgId, item ->
            itemF.complete(Optional.of(item)).
        match(NoSuchItem.class, e -> e.msgId == msgId, e ->
            itemF.complete(Optional.empty())).
        build()
    );
    return itemF;
}
```

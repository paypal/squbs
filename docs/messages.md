
# Message Guidelines

Pekko actors communicate via immutable messages. These can be defined anywhere in code. As squbs deals with messages
communicated across cubes, such messages will have to be defined in a message project (or jar) that is a dependency
of both the sender and receiver of these messages. These message projects commonly have a single file in a single
package.

Messages must be defined as immutable case classes (don't use vars in your case class definitions) or case objects in Scala, or immutable Java beans with constructors and only getters, no setters if they are written in Java.
Messages are generally very simple and do not contain logic. Multiple message case classes or case objects
are declared in a particular Scala file. Java classes are required to have one message class/type per file unless static inner classes are used.

Message jars should not have other dependencies. Ideally, they are all self-contained. Senders and/or receivers of such
messages should not be subject to additional dependencies introduced by messages.

## Constructing messages

Following the Scala case class and case object pattern, construction of the messages are very straightforward and do not
need an explicit call into the constructor. Case classes implicitly generate an associated factory object with proper
apply and unapply methods allowing them to be pattern-matched very easily. Immutable Java beans carry much of the same properties as a Scala case class. However, their construction requires the `new` keyword. Moreover, they do not have a proper `equals()` and `hashcode()` implementations generated for them. Moreover, they cannot be used for Scala pattern extraction and have limited capabilities for Scala pattern matching. For projects with combinations of Java and Scala implementations, message declarations as Scala case classes (not case objects) is recommended.

When integrating messages with database objects or other dependent infrastructure, it is common to provide message
construction directly from these classes. Yet, we MUST NOT declare associate factory objects to provide apply methods
to construct messages from the database object. Doing so would subject the message jar to dependencies on such database
infrastructure. All other cubes using the message will consequently be subject to such database infrastructure
dependencies.

A common pattern used for message construction is to provide a "Message" object inside the cube or package using
such database (or other) infrastructure. This Message object provides a set of apply methods that
the actors will use to construct the message, for instance from mutable data objects. To construct a message from such
an object, the caller just needs to call

```
  targetActorRef ! Message(myDBObject)
```

in Scala, or

```
  targetActorRef.tell(Message.apply(myDBObject), getSelf());
```

in Java.

This way the construction of messages which is dependent on the infrastructure will be contained in the cube producing
such messages. Such dependencies won't leak to consumers of the message.

## Dealing with Large, Complex Messages

In some instances, especially with data objects, these objects have a class hierarchy and heavyweight constructors
that could not easily be dealth with in a simple case class. The number of fields can be far beyond what is possible in
case classes making it unappealing to do field pattern matching. Complex messages such as purchase orders, invoices
commonly fall into this category. These limitations only apply to Scala case classes and not Java beans.

The strategy to deal with such complex objects is to provide the message as traits declaring all fields. If there
is a class hierarchy, subtypes should also be represented as traits extending from proper super type. This is done
in the message project or jar.

Then the Message object in the originating cube will commonly declare the concrete (or abstract) implementation of
these messages with proper constructors from the mutable data objects. It is important to ensure the concrete or
abstract implementation provides no functionality and should not declare additional fields except private ones
to support the construction. In essence, it only implements the constructors to create the object extending the trait.

If fields defined in the trait or any extending trait derives its value from other fields that are set in the
constructor implementation, these fields need to be marked as lazy to avoid NullPointerExceptions during initialization.
This is because the trait will try to initialize all the fields at construction. The derived fields would reference
not-yet set fields to be set in the constructor. Lazy initialization causes these fields to reference the other fields
on first use, which is normally far after the construction of the object.

By following this pattern, messages stay immutable and the message project would not add any dependencies on database
or other infrastructure that can be propagated to the message consumer's dependency chain.

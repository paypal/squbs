squbs
=====

Welcome to squbs (pronounced "skewbs")! We will assimilate your technology and culture. Resistance is futile.

squbs is a modular actor-based services framework build on top of Scala, Akka, and Spray. It is modeled after social and organizational paradigms dividing code into symbiotic groups or modules called "cubes" that talking to each others asynchronously and working in synergy in an ongoing attempt to evolve and perfect themselves.

First some definitions.

Unicomplex: The core of squbs managing the actor system, bootstrapping, and possible hot upgrading of the cubes.

Cube: A self container module consisting of its own supervisory actor that gets bootstrapped. The cube's supervisory actor is looked up by name (actorFor) or by actorSelection and receives asynchronous messages through its actor interface.

Messages: Typed, asynchronous messages sent between actors.

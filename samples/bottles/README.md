Bottles Sample
==============

The bottle sample is a simple example using the modular structure of squbs and streaming service responses. It will stream lyrics from our famous bottles song to your browser. It has the following modules.

* The *bottlecube* is the module containing the logic for generating the interesting events. All cubes only know about each others via ActorSelection. No type dependencies between cubes and services. 

* The *bottlemsgs* project only contains message types (case classes) used for talking to the bottlecube. Message projects MUST NOT contain any logic.

* The *bottlesvc* starts the REST service and listens to the port. Upon arrival of a request, it sends a message to bottlecube which then gradually sends the lyrics of the song back to bottlesvc to be streamed out.

The picture below shows the interaction between the modules:
![Bottle sample modules](images/bottlesample.png)


Getting Started
---------------

1. `cd samples/bottles`
1. `sbt bottlesvc/run`
1. Go to `http://localhost:8080/bottles/index.html`


*Most important - have fun!*


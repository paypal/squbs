Bottles Sample
==============

The bottle sample is a simple example using the modular structure of squbs and streaming service responses. It will stream lyrics from our famous bottles song to your browser. It has the following modules.

* The *bottlecube* is the module containing the logic for generating the interesting events. All cubes only know about each others via ActorSelection. No type dependencies between cubes and services. 

* The *bottlemsgs* project only contains message types (case classes) used for talking to the bottlecube. Message projects MUST NOT contain any logic.

* The *bottlesvc* starts the REST service and listens to the port. Upon arrival of a request, it sends a message to bottlecube which then gradually sends the lyrics of the song back to bottlesvc to be streamed out.

The picture below shows the interaction between the modules:
![alt text](/images/bottlesample.png "Bottle sample modules")


Getting Started
---------------

1. Clone the [squbs](https://github.corp.ebay.com/Squbs/squbs) repo.

2. Build the unicomplex by running "sbt clean update package" from the squbs-unicomplex directory. Note that steps 1 and 2 are not needed once we provide the unicomplex into the repo.

3. Clone this git repo.

4. Build the whole project with "sbt clean update package" from the directory.

5. Start the server using "sbt bottlesvc/run" from the root directory.

6. Go to http://localhost:8080/bottles/index.html


Most important - have fun!
--------------------------

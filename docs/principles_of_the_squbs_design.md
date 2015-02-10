#Principles of the squbs design

The design of the squbs platform centers around the following design principles:

1. **Separation of Concerns**
  
   Application code should focus on application and/or business logic. Lifecycle and infrastructure concerns should be left to the underlying infrastructure. Infrastructure just needs input from the application where such input cannot be automatically derived.  

2. **Loose Coupling**

   Tangling of large systems comes from tight coupling of the type system. Akka, being a message passing system, can avoid type coupling altogether allowing for modules to live together without any type dependency on each others. However, the common way of starting actors needs actors to have type coupling between each others. For instance, `context.actorOf(Props[MyActorType])` requires the caller to import MyActorType and thus causes a type dependency to `MyActorType`. Such couplings cannot be categorized as loose coupling.
   
3. **DRY**  

   Don't Repeat Yourself. The drop-in design of squbs allows for at-most-once coupling. This generally means modules can be dropped in by just adding the module to the dependency list or runtime classpath. No real call into the module is required as this will cause dependencies both in the code and in the dependency list/classpath. Removing the loosely-coupled module will therefore be as easy as removing it from the runtime classpath or dependency.
   
4. **Minimize Duplication of Effort**

   Most of the low-level components of the application that deal with application lifecycle and monitoring are common across applications. A minimal framework dealing with the lifecycle management will hide and abstract away such duplications. This will standardize the PaaS and load balancer interfaces and allows developers to focus on their concern (application logic) and dramatically reduces the variation of scripts and configurations needed to support the applications.
   
5. **Thin Platform**

   We realize the emergence of the thin platform and try to keep squbs as lightweight and non-imposing as possible. As a result, we have the Unicomplex which is the core container in charge of bootstrapping and manageing the state and lifecycle of the runtime. It pulls in virtually no dependency beyond Scala, Akka, and Spray and does not impose further requirements or frameworks on the developer. Any other framework can be included by the developer as needed.

  
##Common Questions/Concerns from Akka Developers

**Developer:** I want to start from my own main

**Q:** How do you wish to start supporting infrastructure such as Perfmon, ValidateInternals?

**Developer:** I will call the initializers for all the infrastructure

**Q:** So you need both the dependency and the application to call into initialization code? Is this Separation of Concerns? Is this Loose Coupling? Is this DRY?

**Developer:** …

**Q:** How could the PaaS lifecycle management know how to start and stop the application?

**Developer:** We can provide lifecycle scripts.

**Q:** Does this mean we need separate lifecycle scripts for each application? Would this not be duplication of effort to have different scripts for each application?

**Developer:** …

**Q:** How could we know you're ready to take traffic?

**Developer:** We would need to write our own health check page.

**Q:** How would PaaS and load balancer configuration know how to find your health check page and what to find in there?

**Developer:** We would need to configure PaaS and Load Balancer for the application.

**Q:** So we need to have separate configurations for each application?

**Developer:** Yes

**Q:** How are we going to maintain these many configuration? Isn't this duplication of effort? Would we achieve separation of concern with this? Should we not standardize?

**Developer:** …

**Q:** How can the cloud environment cleanly shut down the application such as at flex-up/flex-down

**Developer:** Call my shutdown script

**Q:** How do you make sure the shutdown script gracefully terminates the process without causing any state and data corruption?

**Developer:** We take care of graceful shutdown of the application.

**Q:** So each application will have to take care of this themselves?

**Developer:** Yes

**Q:** Shutdown is not trivial. What if there are bugs in the shutdown process.

**Developer:** Each team will have to take care of their own process.

**Q:** Would this be duplication of effort? Should the application be more concerned about business logic rather than lifecycle management? Would this achieve separation of concerns?

**Developer:** …



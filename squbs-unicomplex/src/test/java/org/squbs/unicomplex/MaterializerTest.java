package org.squbs.unicomplex;

import akka.actor.ActorSystem;
import akka.stream.Materializer;

public class MaterializerTest {

    public static Materializer getMaterializer(ActorSystem system, String name) {
       return Unicomplex.get(system).materializer(name);
    }
}

/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.testkit;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import org.squbs.lifecycle.GracefulStop$;
import org.squbs.unicomplex.Unicomplex$;
import org.squbs.unicomplex.UnicomplexBoot;
import org.squbs.unicomplex.UnicomplexExtension;

public class CustomTestKitJ {
    static ActorSystem actorSystem;

    public static void beforeClass(UnicomplexBoot boot) {
        actorSystem = boot.actorSystem();
        CustomTestKit$.MODULE$.checkInit(actorSystem);
    }

    public static void afterAll() {
        UnicomplexExtension unicomplex = Unicomplex$.MODULE$.get(actorSystem);
        unicomplex.uniActor().tell(GracefulStop$.MODULE$, ActorRef.noSender());
    }
}

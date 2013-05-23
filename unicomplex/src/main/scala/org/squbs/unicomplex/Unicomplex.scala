package org.squbs.unicomplex

import akka.actor._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

object Unicomplex {
  
  // TODO: Make ActorSystem name configurable.
  val actorSystem = ActorSystem("unicomplex")

}

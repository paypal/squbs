package org.squbs.unicomplex

import akka.actor.{Actor, ActorSystem, ActorLogging, OneForOneStrategy, Props}
import akka.actor.SupervisorStrategy._
import concurrent.duration._

object Unicomplex {
  
  // TODO: Make ActorSystem name configurable.
  implicit val actorSystem = ActorSystem("squbs")
  
  implicit val uniActor = actorSystem.actorOf(Props[Unicomplex], "unicomplex")
  
}

case object StartWebService
case object Ack

/**
 * The Unicomplex actor is the supervisor of the Unicomplex.
 * It starts actors that are part of the Unicomplex.
 */
class Unicomplex extends Actor with ActorLogging {
  
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case e: Exception => 
        log.warning(s"Received ${e.getClass.getName} with message ${e.getMessage} from ${sender.path}")
        Restart
    }
    
  def receive = {
    case StartWebService =>
      ServiceRegistry.startWebService      
      sender ! Ack 
    case msg: Any =>
      log.info("Received: {}", msg.toString)
      
  }  
}
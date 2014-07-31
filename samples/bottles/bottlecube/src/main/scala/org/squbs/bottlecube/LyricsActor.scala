package org.squbs.bottlecube

import akka.actor._
import concurrent.duration._

import org.squbs.unicomplex.Unicomplex._

import org.squbs.bottlemsgs._


class LyricsDispatcher extends Actor with ActorLogging {
  
  def receive = {        
    case StartEvents => context.actorOf(Props[LyricsActor]) forward StartEvents
  }
  
}

class LyricsActor extends Actor with ActorLogging {
  
  case class TimedUp(counter: Int)

  import Lyrics._
  
  val system = context.system
  import system.dispatcher
  import system.scheduler
  
  val start = 99
  var target:ActorRef = null
  
  def nextEvent(n: Int) = {
      target ! Event(lyric(n))
      scheduler.scheduleOnce(n * 50 milliseconds) {
        self ! TimedUp(n - 1)
      }               
  }
    
  def receive = {
    case StartEvents =>
      target = sender
      nextEvent(start)
      
    case TimedUp(0) =>
      target ! Event(lyricEnd(start))
      target ! EndEvents
      context.stop(self)
      
    case TimedUp(n) => nextEvent(n)
  }
}

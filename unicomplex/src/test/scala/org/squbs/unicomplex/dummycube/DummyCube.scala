package org.squbs.unicomplex.dummycube

import akka.actor.{Props, ActorLogging, Actor}
import org.squbs.unicomplex.{PrependedMsg, Constants, AppendedMsg, EchoMsg}
import org.squbs.lifecycle.{GracefulStop, GracefulStopHelper}

class AppendActor extends Actor with ActorLogging with GracefulStopHelper {

  def receive = {
    case EchoMsg(msg) => sender ! AppendedMsg(msg + Constants.SUFFIX)

    case GracefulStop => defaultLeafActorStop
  }
}

class DummyPrependActor extends Actor with ActorLogging with GracefulStopHelper {

  def receive = {

    case echoMsg @ EchoMsg(msg) => context.actorOf(Props[ActualPrependActor]) forward echoMsg

    case GracefulStop => defaultMidActorStop(context.children)
  }
}

private class ActualPrependActor extends Actor with ActorLogging with GracefulStopHelper {

  def receive = {

    case EchoMsg(msg) => sender ! PrependedMsg(Constants.PREFIX + msg)

    case GracefulStop => defaultLeafActorStop
  }
}

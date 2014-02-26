package org.squbs.pattern

import akka.testkit.TestKit
import akka.actor.{ActorContext, Props, ActorSystem}
import org.zeromq.ZMQ.Socket
import org.scalatest.FunSuite
import org.zeromq.{ZFrame, ZMQ}
import scala.concurrent.duration._

/**
 * Created by huzhou on 2/25/14.
 */
class ZRouterSocketActor extends ZSocketOnAkka {

  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
    printf("[router] consume\n")
    context.self ! zEnvelop
  }

  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
    printf("[router] reply\n")
    zEnvelop.send(zSocket)
  }

  override def unknown(msg: Any, zSocket: Socket): Unit = {
    printf("[router] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
  }
}

class ZDealerSocketActor extends ZSocketOnAkka {

  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
    printf("[dealer] consume\n")
  }

  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
    printf("[dealer] reply\n")
    zEnvelop.send(zSocket)
  }

  override def unknown(msg: Any, zSocket: Socket): Unit = {
    printf("[dealer] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
  }
}

class ZSocketActorSpec extends TestKit(ActorSystem("testZSocket")) with FunSuite {

  test("test req/rep socket"){

    val routerActor = system.actorOf(Props[ZRouterSocketActor])

    routerActor ! SocketType(ZMQ.ROUTER)
    routerActor ! Identity("zmq-rep")
    routerActor ! Bind("tcp://127.0.0.1:5555")

    val dealerActor = system.actorOf(Props[ZDealerSocketActor])

    dealerActor ! SocketType(ZMQ.DEALER)
    dealerActor ! Identity("zmq-req")
    dealerActor ! Connect("tcp://127.0.0.1:5555")
    dealerActor ! ZEnvelop(new ZFrame("zmq-req"), Seq(new ZFrame(s"request-${System.nanoTime}")))

    receiveOne(1000 millis)
  }
}

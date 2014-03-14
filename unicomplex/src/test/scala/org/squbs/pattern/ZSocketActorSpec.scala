package org.squbs.pattern

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorContext, Props, ActorSystem}
import org.scalatest.{Matchers, FunSuiteLike}
import org.zeromq.{ZFrame, ZMQ}
import scala.concurrent.duration._


/**
 * Created by huzhou on 2/25/14.
 */
//
//class ZRouterSocketActor extends ZSocketOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[router] consume\n")
//    context.self ! zEnvelop
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[router] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[router] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//class ZDealerSocketActor extends ZSocketOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[dealer] consume\n")
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[dealer] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[dealer] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//class ZPubSocketActor extends ZProducerOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[pub] consume\n")
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[pub] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[pub] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//class ZSubSocketActor extends ZSocketOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[sub] consume\n")
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[sub] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[sub] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//class ZPushSocketActor extends ZProducerOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[push] consume\n")
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[push] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[push] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//class ZPullSocketActor extends ZSocketOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[pull] consume\n")
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[pull] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[pull] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//class ZPairSocketActor extends ZSocketOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[pair] consume\n")
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[pair] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[pair] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//
//class ZReqSocketActor extends ZBlockingOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[req] consume\n")
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[req] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[req] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}
//
//
//class ZRepSocketActor extends ZSocketOnAkka {
//
//  override def consume(zEnvelop: ZEnvelop, context:ActorContext): Unit = {
//    printf("[rep] consume\n")
//    context.self ! zEnvelop
//  }
//
//  override def reply(zEnvelop: ZEnvelop, zSocket: Socket) = {
//    printf("[rep] reply\n")
//    zEnvelop.send(zSocket)
//  }
//
//  override def unknown(msg: Any, zSocket: Socket): Unit = {
//    printf("[rep] [unknown:%s] %s\n", new String(zSocket.getIdentity, ZSocketOnAkka.utf8), msg)
//  }
//}

class ZSocketActorSpec extends TestKit(ActorSystem("testZSocket")) with ImplicitSender with FunSuiteLike with Matchers {

  test("test router/dealer socket"){

    val routerActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.ROUTER, None, None, None)))

    routerActor ! Identity("zmq-router")
    routerActor ! Bind("tcp://127.0.0.1:5555")

    val dealerActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.DEALER, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    dealerActor ! Identity("zmq-dealer")
    dealerActor ! Connect("tcp://127.0.0.1:5555")
    dealerActor ! ZEnvelop(new ZFrame("zmq-dealer"), Seq(new ZFrame(s"request-${System.nanoTime}")))

    receiveOne(1000 millis)
  }

  test("test pub/sub socket"){

    val pubActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PUB, None, None, None)))

    pubActor ! Identity("zmq-pub")
    pubActor ! Bind("tcp://127.0.0.1:5556")

    val subActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.SUB, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    subActor ! Identity("zmq-sub")
    subActor ! Connect("tcp://127.0.0.1:5556")
    subActor ! ZEnvelop(new ZFrame("zmq-topic"), Seq())

    for(i <- 1 to 1000){
      pubActor ! ZEnvelop(new ZFrame("zmq-topic"), Seq(new ZFrame("some content")))
    }

    receiveOne(1000 millis)
  }

  test("test push/pull socket"){

    val pushActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PUSH, None, None, None)))

    pushActor ! Identity("zmq-push")
    pushActor ! Bind("tcp://127.0.0.1:5557")

    val pullActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PULL, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    pullActor ! Identity("zmq-pull")
    pullActor ! Connect("tcp://127.0.0.1:5557")

    for(i <- 1 to 1000){
      pushActor ! ZEnvelop(new ZFrame("zmq-downstream"), Seq(new ZFrame("some content")))
    }

    receiveOne(1000 millis)
  }

  test("test pair/pair socket"){

    val oneActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PAIR, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    oneActor ! Identity("zmq-one")
    oneActor ! Bind("tcp://127.0.0.1:5558")

    val pairActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PAIR, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    pairActor ! Identity("zmq-two")
    pairActor ! Connect("tcp://127.0.0.1:5558")

    oneActor ! ZEnvelop(new ZFrame("zmq-one"), Seq(new ZFrame("some content")))
    pairActor ! ZEnvelop(new ZFrame("zmq-two"), Seq(new ZFrame("some content")))

    receiveN(2, 1000 millis)
  }

  test("test req/rep socket"){

    val reqActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REQ, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    reqActor ! Identity("zmq-req")
    reqActor ! Bind("tcp://127.0.0.1:5559")

    val repActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REP, None, None, None)))

    repActor ! Identity("zmq-rep")
    repActor ! Connect("tcp://127.0.0.1:5559")

    reqActor ! ZEnvelop(new ZFrame("zmq-req"), Seq(new ZFrame("some content")))

    receiveOne(1000 millis)
  }
}

package org.squbs.pattern

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorContext, Props, ActorSystem}
import org.scalatest.{Matchers, FunSuiteLike}
import org.zeromq.{ZFrame, ZMQ}
import scala.concurrent.duration._


/**
 * Created by huzhou on 2/25/14.
 */
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

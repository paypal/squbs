package org.squbs.pattern

import org.zeromq.ZMQ
import org.squbs._
import akka.actor.{ActorContext, Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import scala.concurrent.duration._
import org.scalatest.{Matchers, FunSuiteLike}


/**
 * Created by huzhou on 2/25/14.
 */
class ZSocketActorSpec extends TestKit(ActorSystem("testZSocket")) with ImplicitSender with FunSuiteLike with Matchers {

  test("test router/dealer socket"){

    val port = nextPort
    val routerActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.ROUTER, None, None, None)))

    routerActor ! Identity("zmq-router")
    routerActor ! Bind(s"tcp://127.0.0.1:$port")

    val dealerActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.DEALER, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    dealerActor ! Identity("zmq-dealer")
    dealerActor ! Connect(s"tcp://127.0.0.1:$port")
    dealerActor ! ZEnvelop(Some(ByteString("zmq-dealer")), Seq(ByteString(s"request-${System.nanoTime}")))

    receiveOne(1000 millis)
  }

  test("test pub/sub socket"){

    val port = nextPort
    val pubActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PUB, None, None, None)))

    pubActor ! Identity("zmq-pub")
    pubActor ! Bind(s"tcp://127.0.0.1:$port")

    val subActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.SUB, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    subActor ! Identity("zmq-sub")
    subActor ! Connect(s"tcp://127.0.0.1:$port")
    subActor ! ZEnvelop(Some(ByteString("zmq-topic")), Seq())

    for(i <- 1 to 1000){
      pubActor ! ZEnvelop(Some(ByteString("zmq-topic")), Seq(ByteString("some content")))
    }

    receiveOne(1000 millis)
  }

  test("test push/pull socket"){

    val port = nextPort
    val pushActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PUSH, None, None, None)))

    pushActor ! Identity("zmq-push")
    pushActor ! Bind(s"tcp://127.0.0.1:$port")

    val pullActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PULL, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    pullActor ! Identity("zmq-pull")
    pullActor ! Connect(s"tcp://127.0.0.1:$port")

    for(i <- 1 to 1000){
      pushActor ! ZEnvelop(None, Seq(ByteString("some content")))
    }

    receiveOne(1000 millis)
  }

  test("test pair/pair socket"){

    val port = nextPort
    val oneActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PAIR, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    oneActor ! Identity("zmq-one")
    oneActor ! Bind(s"tcp://127.0.0.1:$port")

    val pairActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PAIR, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    pairActor ! Identity("zmq-two")
    pairActor ! Connect(s"tcp://127.0.0.1:$port")

    oneActor ! ZEnvelop(None, Seq(ByteString("some content")))
    pairActor ! ZEnvelop(None, Seq(ByteString("some content")))

    receiveN(2, 1000 millis)
  }

  test("test req/rep socket"){

    val port = nextPort
    val reqActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REQ, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    reqActor ! Identity("zmq-req")
    reqActor ! Bind(s"tcp://127.0.0.1:$port")

    val repActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REP, None, None, None)))

    repActor ! Identity("zmq-rep")
    repActor ! Connect(s"tcp://127.0.0.1:$port")

    reqActor ! ZEnvelop(Some(ByteString("zmq-req")), Seq(ByteString("some content")))

    receiveOne(1000 millis)
  }
}

package org.squbs.pattern

import org.zeromq.ZMQ
import akka.actor.{ActorContext, Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import scala.concurrent.duration._
import org.scalatest.{BeforeAndAfterAll, Matchers, FunSuiteLike}
import org.squbs.testkit.util.Ports


/**
 * Created by huzhou on 2/25/14.
 */
class ZSocketActorSpec extends TestKit(ActorSystem("testZSocket")) with ImplicitSender with FunSuiteLike with Matchers with BeforeAndAfterAll {

  override def afterAll = system.shutdown

  test("test router/dealer socket"){

    val port = Ports.available(2888, 5000)
    val routerActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.ROUTER, None, None, None)))

    routerActor ! Identity("zmq-router")
    routerActor ! Bind(s"tcp://127.0.0.1:$port")

    val dealerActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.DEALER, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    dealerActor ! Identity("zmq-dealer")
    dealerActor ! Connect(s"tcp://127.0.0.1:$port")
    dealerActor ! Seq(ByteString(s"request-${System.nanoTime}"))

    receiveOne(1000 millis)
  }

  test("test pub/sub socket"){

    val port = Ports.available(2888, 5000)
    val pubActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PUB, None, None, None)))

    pubActor ! Identity("zmq-pub")
    pubActor ! Bind(s"tcp://127.0.0.1:$port")

    val subActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.SUB, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    subActor ! Identity("zmq-sub")
    subActor ! Connect(s"tcp://127.0.0.1:$port")
    subActor ! Seq(ByteString("zmq-topic"))

    for(i <- 1 to 1000){
      pubActor ! Seq(ByteString("zmq-topic"), ByteString("some content"))
    }

    receiveOne(1000 millis)
  }

  test("test push/pull socket"){

    val port = Ports.available(2888, 5000)
    val pushActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PUSH, None, None, None)))

    pushActor ! Identity("zmq-push")
    pushActor ! Bind(s"tcp://127.0.0.1:$port")

    val pullActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.PULL, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    pullActor ! Identity("zmq-pull")
    pullActor ! Connect(s"tcp://127.0.0.1:$port")

    for(i <- 1 to 1000){
      pushActor ! Seq(ByteString("some content"))
    }

    receiveOne(1000 millis)
  }

  test("test pair/pair socket"){

    val port = Ports.available(2888, 5000)
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

    oneActor ! Seq(ByteString("some content"))
    pairActor ! Seq(ByteString("some content"))

    receiveN(2, 1000 millis)
  }

  test("test req/rep socket"){

    val port = Ports.available(2888, 5000)
    val reqActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REQ, Some((zEnvelop:ZEnvelop, context:ActorContext) => {
      self ! "got it"
    }), None, None)))

    reqActor ! Identity("zmq-req")
    reqActor ! Bind(s"tcp://127.0.0.1:$port")

    val repActor = system.actorOf(Props(ZSocketOnAkka(ZMQ.REP, None, None, None)))

    repActor ! Identity("zmq-rep")
    repActor ! Connect(s"tcp://127.0.0.1:$port")

    reqActor ! Seq(ByteString("some content"))

    receiveOne(1000 millis)
  }
}

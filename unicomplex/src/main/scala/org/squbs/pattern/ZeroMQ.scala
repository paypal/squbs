package org.squbs.pattern

import scala.concurrent.duration._
import akka.actor.{ActorContext, FSM, Actor}
import org.zeromq.ZMQ.Socket
import org.zeromq.ZFrame
import org.zeromq.ZContext
import org.zeromq.ZMsg
import org.zeromq.ZMQ
import java.nio.charset.Charset
import scala.reflect.ClassTag

/**
 * Created by huzhou on 2/25/14.
 */

private[pattern] sealed trait ZSocketState
private[pattern] case object ZSocketUninitialized extends ZSocketState
private[pattern] case object ZSocketActive extends ZSocketState

//TODO complete the full list of configurations allowed by ZMQ
//TODO support REQ/REP socket types, which doesn't support NONE-BLOCKING receives as current!
//TODO support PUSH/PULL socket types
//TODO support PAIR/PAIR socket types
//ROUTER/DEALER is happy
//PUB/SUB is happy
case class SocketType(val `type`:Int)
case class Identity(val identity:String)
case class ReceiveHWM(val hwm:Int)
case class SendHWM(val hwm:Int)
case class MaxMessageSize(val size:Long)
case class MaxDelay(val delay:Long)
case class Bind(val address:String)
case class Connect(val address:String)

private[pattern] sealed trait ZSocketData

/**
 * CONFIGURATIONS DATA
 * @param identity
 * @param socketType
 * @param receiveHWM
 * @param sendHWM
 * @param maxDelay
 */
private[pattern] case class Settings(val socketType:Int,
                                     val identity:Option[String],
                                     val receiveHWM:Option[Int],
                                     val sendHWM:Option[Int],
                                     val maxMessageSize:Option[Long],
                                     val maxDelay:Option[Long]) extends ZSocketData

/**
 * RUNTIME DATA
 * @param socket
 * @param maxDelay
 */
private[pattern] case class Runnings(val socket:Socket, val maxDelay:Long) extends ZSocketData

private[pattern] case class ReceiveAsync(val delay:Long) {

  def doubleDelay(cap:Long) = ReceiveAsync(if(delay == 0L) 1L else math.min(delay * 2L, cap))
}

case class ZEnvelop(val identity:ZFrame, val payload:Seq[ZFrame]) {

  def send(zSocket:Socket) = {

    zSocket.getType match {
      case ZMQ.SUB =>
        zSocket.subscribe(identity.getData)
      case ZMQ.PULL =>
        //don't do anything, or raise exception
      case _ =>
        identity.send(zSocket, ZMQ.SNDMORE)
        payload.foreach(f => f.send(zSocket, if(f == payload.last) 0 else ZMQ.SNDMORE))
    }
  }
}

trait ZSocketOnAkka extends Actor with FSM[ZSocketState, ZSocketData]{

  def zContext:ZContext = new ZContext

  def consume(zEnvelop:ZEnvelop, context:ActorContext):Unit

  def reply(zEnvelop:ZEnvelop, zSocket:Socket):Unit

  def unknown(msg:Any, zSocket:Socket):Unit

  def create(settings:Settings) = {

    printf("[create] settings:%s\n", settings)

    val zSocket = zContext.createSocket(settings.socketType)

    settings.identity.foreach(identity => zSocket.setIdentity(identity.getBytes(ZSocketOnAkka.utf8)))
    settings.receiveHWM.foreach(hwm => zSocket.setRcvHWM(hwm))
    settings.sendHWM.foreach(hwm => zSocket.setSndHWM(hwm))
    settings.maxMessageSize.foreach(size => zSocket.setMaxMsgSize(size))

    zSocket
  }

  def activate(zSocket:Socket, settings:Settings) = {

    val maxDelay = settings.maxDelay.getOrElse(ZSocketOnAkka.defaultMaxDelay)
    self ! ReceiveAsync(maxDelay)
    goto(ZSocketActive) using Runnings(zSocket, maxDelay)
  }

  //init state
  startWith(ZSocketUninitialized, Settings(-1, None, None, None, None, None))

  //configuration
  when(ZSocketUninitialized){

    case Event(SocketType(socketType), origin:Settings) =>
      stay using(origin.copy(socketType = socketType))

    case Event(Identity(identity), origin:Settings) =>
      stay using(origin.copy(identity = Some(identity)))

    case Event(ReceiveHWM(hwm), origin:Settings) =>
      stay using(origin.copy(receiveHWM = Some(hwm)))

    case Event(SendHWM(hwm), origin:Settings) =>
      stay using(origin.copy(sendHWM = Some(hwm)))

    case Event(MaxMessageSize(size), origin:Settings) =>
      stay using(origin.copy(maxMessageSize = Some(size)))

    case Event(MaxDelay(delay), origin:Settings) =>
      stay using(origin.copy(maxDelay = Some(delay)))

    case Event(bind:Bind, origin:Settings) =>
      val zSocket = create(origin)
      zSocket.bind(bind.address)
      activate(zSocket, origin)

    case Event(connect:Connect, origin:Settings) =>
      val zSocket = create(origin)
      zSocket.connect(connect.address)
      activate(zSocket, origin)

  }

  //runtime
  when(ZSocketActive){

    //inbound
    case Event(msg @ ReceiveAsync(delay), Runnings(socket, maxDelay)) =>
//      printf("[recv]\n")
      val zMessage = ZMsg.recvMsg(socket, ZMQ.DONTWAIT)
      val identity = zMessage.pop
      if(identity.hasData){
        //got real message, consume all frames, and resume by sending ReceiveAsync to self again
        var zFrames = Seq[ZFrame]()
        while(!zMessage.isEmpty){
          zFrames = zFrames :+ zMessage.pop
        }
        consume(ZEnvelop(identity, zFrames), context)
        self ! ReceiveAsync(1L)
      }
      else{
        //no real message comes in, delay further to avoid exhaust CPU with cap of maxDelay
        import scala.concurrent.ExecutionContext.Implicits.global

        context.system.scheduler.scheduleOnce(delay millis, self, msg.doubleDelay(maxDelay))
      }
      stay
    //outbound
    case Event(msg:ZEnvelop, Runnings(socket, maxDelay)) =>
      reply(msg, socket)
      stay

  }

  whenUnhandled {
    case Event(msg, Runnings(socket, maxDelay)) =>
      unknown(msg, socket)
      stay
  }
}

private[pattern] case object ZPublisherActive extends ZSocketState

//Producer only knows about sending frames out through ZSocket
//PUSH/PUB are typical examples
trait ZProducerOnAkka extends ZSocketOnAkka {

  override def activate(zSocket:Socket, settings:Settings) = {

    printf("[pub] activate\n")
    val maxDelay = settings.maxDelay.getOrElse(ZSocketOnAkka.defaultMaxDelay)
    goto(ZPublisherActive) using Runnings(zSocket, maxDelay)
  }

  startWith(ZSocketUninitialized, Settings(-1, None, None, None, None, None))

  //runtime
  when(ZPublisherActive){
    //outbound
    case Event(msg:ZEnvelop, Runnings(socket, maxDelay)) =>
      reply(msg, socket)
      stay
  }
}

object ZSocketOnAkka {

  final val utf8 = Charset.forName("utf-8")

  final val defaultMaxDelay = 128L//128 millis

  //pinned dispatcher to avoid thread switchings
  def Props[A <: Actor: ClassTag] = akka.actor.Props[A].withDispatcher("pinned-dispatcher")

}
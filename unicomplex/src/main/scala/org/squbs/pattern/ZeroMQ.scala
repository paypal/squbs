package org.squbs.pattern

import scala.concurrent.duration._
import akka.actor.{ActorContext, FSM, Actor}
import org.zeromq.ZMQ.Socket
import org.zeromq.ZContext
import org.zeromq.ZMsg
import org.zeromq.ZMQ
import java.nio.charset.Charset
import scala.reflect.ClassTag
import akka.util.ByteString

/**
 * Created by huzhou on 2/25/14.
 */

private[pattern] sealed trait ZSocketState
private[pattern] case object ZSocketUninitialized extends ZSocketState
private[pattern] case object ZSocketActive extends ZSocketState

//TODO complete the full list of configurations allowed by ZMQ
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
 * @param receiveHWM
 * @param sendHWM
 * @param maxDelay
 */
private[pattern] case class Settings(val identity:Option[String],
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

private[pattern] case object ReceiveBlock

case class ZEnvelop(val identity:Option[ByteString], val payload:Seq[ByteString]) {

  def send(zSocket:Socket) = {

    zSocket.getType match {
      case ZMQ.SUB =>
        identity.foreach(id => zSocket.subscribe(id.getData))
      case ZMQ.PULL =>
        //don't do anything, or raise exception
        throw new IllegalStateException("cannot send message via PULL socket type")
      case _ =>
        identity.foreach(id => id.send(zSocket, ZMQ.SNDMORE))
        payload.foreach(f => f.send(zSocket, if(f == payload.last) 0 else ZMQ.SNDMORE))
    }
  }
}

trait ZSocketOnAkka extends Actor with FSM[ZSocketState, ZSocketData]{

  val socketType:Int

  def zContext:ZContext = new ZContext

  def consume(zEnvelop:ZEnvelop, context:ActorContext):Unit

  def reply(zEnvelop:ZEnvelop, zSocket:Socket):Unit

  def unknown(msg:Any, zSocket:Socket):Unit

  def create(settings:Settings) = {

    val zSocket = zContext.createSocket(socketType)

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
  startWith(ZSocketUninitialized, Settings(None, None, None, None, None))

  //configuration
  when(ZSocketUninitialized){

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
      val zMessage = ZMsg.recvMsg(socket, ZMQ.DONTWAIT)
      val identity = zMessage.pop
      if(identity.hasData){
        //got real message, consume all frames, and resume by sending ReceiveAsync to self again
        var zFrames = Seq[ByteString]()
        while(!zMessage.isEmpty){
          zFrames = zFrames :+ zFrameToByteString(zMessage.pop)
        }
        consume(ZEnvelop(Some(identity), zFrames), context)
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

    val maxDelay = settings.maxDelay.getOrElse(ZSocketOnAkka.defaultMaxDelay)
    goto(ZPublisherActive) using Runnings(zSocket, maxDelay)
  }

  startWith(ZSocketUninitialized, Settings(None, None, None, None, None))

  //runtime
  when(ZPublisherActive){
    //outbound
    case Event(msg:ZEnvelop, Runnings(socket, maxDelay)) =>
      reply(msg, socket)
      stay
  }
}

private[pattern] case object ZBlockingWritable extends ZSocketState
private[pattern] case object ZBlockingReadable extends ZSocketState

trait ZBlockingOnAkka extends ZSocketOnAkka {

  override def activate(zSocket:Socket, settings:Settings) = {

    goto(ZBlockingWritable) using Runnings(zSocket, -1L);
  }

  startWith(ZSocketUninitialized, Settings(None, None, None, None, None))

  //runtime
  when(ZBlockingWritable){
    case Event(msg:ZEnvelop, r @ Runnings(socket, maxDelay)) =>
      reply(msg, socket)
      self ! ReceiveBlock
      goto(ZBlockingReadable) using r
  }

  when(ZBlockingReadable){
    case Event(ReceiveBlock, r @ Runnings(socket, maxDelay)) =>
      val zMessage = ZMsg.recvMsg(socket, 0)//blocking
      val identity = zMessage.pop
      //got real message, consume all frames, and resume by sending ReceiveAsync to self again
      var zFrames = Seq[ByteString]()
      while(!zMessage.isEmpty){
        zFrames = zFrames :+ zFrameToByteString(zMessage.pop)
      }
      consume(ZEnvelop(Some(identity), zFrames), context)
      goto(ZBlockingWritable) using r
  }
}

object ZSocketOnAkka {

  final val utf8 = Charset.forName("utf-8")

  final val defaultMaxDelay = 128L//128 millis

  final val noConsumption = (zEnvelop:ZEnvelop, context:ActorContext) => ()

  final val noReply = (zEnvelop:ZEnvelop, zSocket:Socket) => ()

  final val noUnknown = (msg:Any, zSocket:Socket) => ()

  //pinned dispatcher to avoid thread switchings
  def Props[A <: Actor: ClassTag] = akka.actor.Props[A].withDispatcher("pinned-dispatcher")

    def apply(`type`:Int, consumeOption:Option[(ZEnvelop, ActorContext) => Unit], replyOption:Option[(ZEnvelop, Socket) => Unit], unknownOption:Option[(Any, Socket) => Unit]) = {

      `type` match {
        case ZMQ.REQ =>
          new ZBlockingOnAkka {

            override val socketType: Int = `type`

            override def consume(zEnvelop: ZEnvelop, context: ActorContext): Unit =
              consumeOption.getOrElse(noConsumption).apply(zEnvelop, context)

            override def reply(zEnvelop: ZEnvelop, zSocket: Socket): Unit =
              replyOption.getOrElse((zEnvelop:ZEnvelop, zSocket:Socket) => {
                zEnvelop.send(zSocket)
              }).apply(zEnvelop, zSocket)

            override def unknown(msg: Any, zSocket: Socket): Unit =
              unknownOption.getOrElse(noUnknown).apply(msg, zSocket)
          }
        case ZMQ.PUB | ZMQ.PUSH =>
          new ZProducerOnAkka {

            override val socketType: Int = `type`

            override def consume(zEnvelop: ZEnvelop, context: ActorContext): Unit =
              consumeOption.getOrElse(noConsumption).apply(zEnvelop, context)

            override def reply(zEnvelop: ZEnvelop, zSocket: Socket): Unit =
              replyOption.getOrElse((zEnvelop:ZEnvelop, zSocket:Socket) => {
                zEnvelop.send(zSocket)
              }).apply(zEnvelop, zSocket)

            override def unknown(msg: Any, zSocket: Socket): Unit =
              unknownOption.getOrElse(noUnknown).apply(msg, zSocket)
          }
        case _ =>
          new ZSocketOnAkka {

            override val socketType: Int = `type`

            override def consume(zEnvelop: ZEnvelop, context: ActorContext): Unit =
              consumeOption.getOrElse(
                if(socketType == ZMQ.ROUTER || socketType == ZMQ.REP)
                  (zEnvelop:ZEnvelop, context:ActorContext) => {
                    context.self ! zEnvelop
                  }
                else
                  noConsumption).apply(zEnvelop, context)

            override def reply(zEnvelop: ZEnvelop, zSocket: Socket): Unit =
              replyOption.getOrElse((zEnvelop:ZEnvelop, zSocket:Socket) => {
                zEnvelop.send(zSocket)
              }).apply(zEnvelop, zSocket)

            override def unknown(msg: Any, zSocket: Socket): Unit =
              unknownOption.getOrElse(noUnknown).apply(msg, zSocket)
          }

      }
  }
}
/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.squbs.unicomplex

import javax.net.ssl.SSLContext

import org.apache.pekko.actor.Actor._
import org.apache.pekko.actor.{ActorContext, ActorRef}
import org.apache.pekko.event.LoggingAdapter
import com.typesafe.config.Config
import org.squbs.pipeline.PipelineSetting
import org.squbs.util.ConfigUtil._

import scala.concurrent.ExecutionContext
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

trait ServiceRegistryBase[A] {

  val log: LoggingAdapter

  protected def listenerRoutes: Map[String, Seq[(A, FlowWrapper, PipelineSetting)]]

  protected def listenerRoutes_=[B](newListenerRoutes: Map[String, Seq[(B, FlowWrapper, PipelineSetting)]]): Unit

  private[unicomplex] def prepListeners(listenerNames: Iterable[String])(implicit context: ActorContext): Unit = {
    listenerRoutes = listenerNames.map { listener =>
      listener -> Seq.empty[(A, FlowWrapper, PipelineSetting)]
    }.toMap

    import org.squbs.unicomplex.JMX._
    register(new ListenerBean(listenerRoutes), prefix + listenersName)
    register(listenerStateMXBean(), prefix + listenerStateName)
  }

  protected def listenerStateMXBean(): ListenerStateMXBean

  private[unicomplex] def registerContext(listeners: Iterable[String], webContext: String, servant: FlowWrapper,
                                          ps: PipelineSetting)(implicit context: ActorContext): Unit

  protected def pathCompanion(s: String): A

  protected def pathLength(p: A): Int

  private[unicomplex] def deregisterContext(webContexts: Seq[String])
                                           (implicit ec: ExecutionContext): Unit = {

    listenerRoutes = listenerRoutes flatMap {
      case (listener, routes) => webContexts map { ctx =>
        val buffer = ListBuffer[(A, FlowWrapper, PipelineSetting)]()
        val path = pathCompanion(ctx)
        routes.foreach {
          entry => if (entry._1 != path) buffer += entry
        }
        (listener, buffer.toSeq)
      }
    }
  }

  private[unicomplex] def startListener(name: String, config: Config, notifySender: ActorRef)
                                       (implicit context: ActorContext): Receive

  private[unicomplex] def isShutdownComplete: Boolean

  private[unicomplex] def stopAll()(implicit context: ActorContext): Unit

  private[unicomplex] def isAnyFailedToInitialize: Boolean

  private[unicomplex] def shutdownState: Receive

  private[unicomplex] def isListenersBound: Boolean

  private[unicomplex] def listenerTerminated(listenerActor: ActorRef): Unit

  private[unicomplex] def portBindings: Map[String, Int]

  case class BindConfig(interface: String, port: Int, localPort: Option[Int],
                        ssLContext: Option[SSLContext], needClientAuth: Boolean)

  protected def bindConfig(config: Config): BindConfig = {
    val interface = if (config getBoolean "full-address") org.squbs.util.ConfigUtil.ipv4
    else config getString "bind-address"
    val port = config getInt "bind-port"
    // assign the localPort only if local-port-header is true
    val localPort = config.getOption[Boolean]("local-port-header") collect {
      case true => port
    }

    val (sslContext, needClientAuth) =
      if (config.getBoolean("secure")) {

        val sslContextClassName = config.getString("ssl-context")
        implicit def sslContext: SSLContext =
          if (sslContextClassName == "default") SSLContext.getDefault
          else {
            try {
              val clazz = Class.forName(sslContextClassName)
              clazz.getMethod("getServerSslContext").invoke(clazz.newInstance()).asInstanceOf[SSLContext]
            } catch {
              case NonFatal(e) =>
                log.error(e, s"Failure obtaining SSLContext from $sslContextClassName.")
                throw e
            }
          }

        (Some(sslContext), config.getBoolean("need-client-auth"))
      } else (None, false)

    BindConfig(interface, port, localPort, sslContext, needClientAuth)
  }

  private[unicomplex] def merge[B, C](oldRegistry: Seq[(A, B, C)], webContext: String, servant: B, pipelineSetting: C,
                                   overrideWarning: => Unit = {}): Seq[(A, B, C)] = {
    val newMember = (pathCompanion(webContext), servant, pipelineSetting)
    if (oldRegistry.isEmpty) Seq(newMember)
    else {
      val buffer = ListBuffer[(A, B, C)]()
      var added = false
      oldRegistry foreach {
        entry =>
          if (added) buffer += entry
          else
          if (entry._1 == newMember._1) {
            overrideWarning
            buffer += newMember
            added = true
          } else {
            if (pathLength(newMember._1) >= pathLength(entry._1)) {
              buffer += newMember += entry
              added = true
            } else buffer += entry
          }
      }
      if (!added) buffer += newMember
      buffer.toSeq
    }
  }
}

case class RegisterContext(listeners: Seq[String], webContext: String, handler: FlowWrapper, ps: PipelineSetting)

object WithWebContext {

  private[unicomplex] val localContext = new ThreadLocal[Option[String]] {
    override def initialValue(): Option[String] = None
  }

  def apply[T](webContext: String)(fn: => T): T = {
    localContext.set(Some(webContext))
    val r = fn
    localContext.set(None)
    r

  }
}

trait WebContext {
  protected final val webContext: String = WithWebContext.localContext.get.get
}

class ListenerBean[A](listenerRoutes: => Map[String, Seq[(A, FlowWrapper, PipelineSetting)]]) extends ListenerMXBean {

  override def getListeners: java.util.List[ListenerInfo] = {
    listenerRoutes.flatMap { case (listenerName, routes) =>
      routes map { case (webContext, servant, _) => // TODO Pass PipelineSetting
        ListenerInfo(listenerName, webContext.toString, servant.actor.toString)
      }
    }.toSeq.asJava
  }
}

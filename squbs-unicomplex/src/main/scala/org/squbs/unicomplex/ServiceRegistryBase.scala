/*
 *  Copyright 2015 PayPal
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
import akka.actor.Actor._
import akka.actor.{ActorRef, ActorContext}
import akka.agent.Agent
import akka.event.LoggingAdapter
import com.typesafe.config.Config
import org.squbs.pipeline.streaming.PipelineSetting
import org.squbs.unicomplex.ConfigUtil._

import scala.concurrent.{Future, ExecutionContext}
import scala.collection.mutable.ListBuffer

trait ServiceRegistryBase[A] {

  val log: LoggingAdapter

  protected def listenerRoutes: Map[String, Agent[Seq[(A, ActorWrapper, PipelineSetting)]]]

  protected def listenerRoutes_=[B](newListenerRoutes: Map[String, Agent[Seq[(B, ActorWrapper, PipelineSetting)]]]): Unit

  private[unicomplex] def prepListeners(listenerNames: Iterable[String])(implicit context: ActorContext) {
    import context.dispatcher
    listenerRoutes = listenerNames.map { listener =>
      listener -> Agent[Seq[(A, ActorWrapper, PipelineSetting)]](Seq.empty)
    }.toMap

    import org.squbs.unicomplex.JMX._
    register(new ListenerBean(listenerRoutes), prefix + listenersName)
  }

  private[unicomplex] def registerContext(listeners: Iterable[String], webContext: String, servant: ActorWrapper,
                                          ps: PipelineSetting) {
    listeners foreach { listener =>
      val agent = listenerRoutes(listener)
      agent.send {
        currentSeq =>
          merge(currentSeq, webContext, servant, ps, {
            log.warning(s"Web context $webContext already registered on $listener. Override existing registration.")
          })
      }
    }
  }

  protected def pathCompanion(s: String): A

  protected def pathLength(p: A): Int

  private[unicomplex] def deregisterContext(webContexts: Seq[String])
                                           (implicit ec: ExecutionContext): Future[Ack.type] = {
    val futures = listenerRoutes flatMap {
      case (_, agent) => webContexts map { ctx => agent.alter {
        oldEntries =>
          val buffer = ListBuffer[(A, ActorWrapper, PipelineSetting)]()
          val path = pathCompanion(ctx)
          oldEntries.foreach {
            entry => if (!entry._1.equals(path)) buffer += entry
          }
          buffer.toSeq
      }
      }
    }
    Future.sequence(futures) map { _ => Ack}
  }

  private[unicomplex] def startListener(name: String, config: Config, notifySender: ActorRef)
                                       (implicit context: ActorContext): Receive

  private[unicomplex] def isShutdownComplete: Boolean

  private[unicomplex] def stopAll()(implicit context: ActorContext): Unit

  private[unicomplex] def isAnyFailedToInitialize: Boolean

  private[unicomplex] def shutdownState: Receive

  private[unicomplex] def isListenersBound: Boolean

  private[unicomplex] def listenerTerminated(listenerActor: ActorRef): Unit

  protected def bindConfig(config: Config) = {
    val interface = if (config getBoolean "full-address") ConfigUtil.ipv4
    else config getString "bind-address"
    val port = config getInt "bind-port"
    // assign the localPort only if local-port-header is true
    val localPort = config getOptionalBoolean "local-port-header" flatMap { useHeader =>
      if (useHeader) Some(port) else None
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
              case e: Throwable =>
                System.err.println(s"WARN: Failure obtaining SSLContext from $sslContextClassName. " +
                  "Falling back to default.")
                SSLContext.getDefault
            }
          }

        (Some(sslContext), config.getBoolean("need-client-auth"))
      } else (None, false)

    (interface, port, localPort, sslContext, needClientAuth)
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
          if (entry._1.equals(newMember._1)) {
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

object WebContext {

  private[unicomplex] val localContext = new ThreadLocal[Option[String]] {
    override def initialValue(): Option[String] = None
  }

  def createWithContext[T](webContext: String)(fn: => T): T = {
    localContext.set(Some(webContext))
    val r = fn
    localContext.set(None)
    r

  }
}

trait WebContext {
  protected final val webContext: String = WebContext.localContext.get.get
}

class ListenerBean[A](listenerRoutes: Map[String, Agent[Seq[(A, ActorWrapper, PipelineSetting)]]]) extends ListenerMXBean {

  override def getListeners: java.util.List[ListenerInfo] = {
    import scala.collection.JavaConversions._
    listenerRoutes.flatMap { case (listenerName, agent) =>
      agent() map { case (webContext, servant, _) => // TODO Pass PipelineSetting
        ListenerInfo(listenerName, webContext.toString(), servant.actor.toString())
      }
    }.toSeq
  }
}
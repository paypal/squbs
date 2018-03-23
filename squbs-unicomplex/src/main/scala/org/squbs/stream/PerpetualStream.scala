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
package org.squbs.stream

import akka.actor.ActorContext
import akka.stream.Supervision._
import akka.stream._
import akka.stream.scaladsl.{RunnableGraph, Sink}
import akka.util.Timeout
import akka.{Done, NotUsed}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.reflect.ClassTag

/**
 * Scala API for perpetual stream that starts and stops with the server.
 * @tparam T The type of the materialized value of the stream.
 */
trait PerpetualStream[T] extends PerpetualStreamBase[T] {

  /**
   * Describe your graph by implementing streamGraph
   *
   * @return The graph.
   */
  def streamGraph: RunnableGraph[T]

  /**
   * The decider to use. Override if not resumingDecider.
   */
  def decider: Supervision.Decider = { t =>
    log.error("Uncaught error {} from stream", t)
    t.printStackTrace()
    Resume
  }

  implicit val materializer: ActorMaterializer =
    ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

  override private[stream] final def runGraph(): T = streamGraph.run()

  override private[stream] final def shutdownAndNotify(): Unit = {
    import context.dispatcher
    shutdown() onComplete { _ => self ! Done }
  }

  def receive: Receive = PartialFunction.empty

  /**
    * Override shutdown to define your own shutdown process or wait for the sink to finish.
    * The default shutdown makes the following assumptions:<ol>
    *   <li>The stream materializes to a Future or a Product (Tuple, List, etc.)
    *       for which the last element is a Future</li>
    *   <li>This Future represents the state whether the stream is done</li>
    *   <li>The stream has the killSwitch as the first processing stage</li>
    * </ol>In which case you do not need to override this default shutdown if there are no further shutdown
    * requirements. In case you override shutdown, it is recommended that super.shutdown() be called
    * on overrides even if the stream only partially meets the requirements above.
    *
    * @return A Future[Done] that gets completed when the whole stream is done.
    */
  def shutdown(): Future[Done] = {
    import context.dispatcher
    matValue match {
      case f: Future[_] =>
        killSwitch.shutdown()
        f.map(_ => Done)
      case p: Product if p.productArity > 0 =>
        p.productElement(0) match {
          case k: KillSwitch => k.shutdown()
          case _ =>
        }
        killSwitch.shutdown()
        p.productElement(p.productArity - 1) match {
          case f: Future[_] => f.map(_ => Done)
          case _ => Future.successful { Done }
        }
      case _ =>
        killSwitch.shutdown()
        Future.successful { Done }
    }
  }
}

trait PerpetualStreamMatValue[T] {
  protected val context: ActorContext

  def matValue(perpetualStreamName: String)(implicit classTag: ClassTag[T]): Sink[T, NotUsed] = {
    implicit val _ = context.system
    implicit val timeout: Timeout = Timeout(10.seconds)
    import akka.pattern.ask

    val responseF = (SafeSelect(perpetualStreamName) ? MatValueRequest).mapTo[Sink[T, NotUsed]]

    // Exception! This code is executed only at startup. We really need a better API, though.
    Await.result(responseF, timeout.duration)
  }
}

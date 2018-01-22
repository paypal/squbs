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

import java.util.concurrent.{CompletableFuture, CompletionStage}
import java.util.function.Consumer

import akka.actor.AbstractActor
import akka.stream.Supervision.{Directive, Resume}
import akka.stream._
import akka.stream.javadsl.{RunnableGraph, Sink}
import akka.util.Timeout
import akka.{Done, NotUsed}
import org.squbs.unicomplex.AbstractFlowDefinition

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.runtime.BoxedUnit

/**
 * Java API for perpetual stream that starts and stops with the server.
 * @tparam T The type of the materialized value of the stream.
 */
abstract class AbstractPerpetualStream[T] extends AbstractActor with PerpetualStreamBase[T] {

  /**
   * Describe your graph by implementing streamGraph
   *
   * @return The graph.
   */
  def streamGraph: RunnableGraph[T]


  /**
   * The decider to use. Override if not resumingDecider.
   */
  def decider: akka.japi.function.Function[Throwable, Directive] =
      new akka.japi.function.Function[Throwable, Directive]() {
        def apply(t: Throwable): Directive = {
          log.error("Uncaught error {} from stream", t)
          t.printStackTrace()
          Resume
        }
      }

  implicit val materializer: ActorMaterializer =
    ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

  override private[stream] final def runGraph(): T = streamGraph.run(materializer)

  override private[stream] final def shutdownAndNotify(): Unit = shutdown().thenAccept(new Consumer[Done] {
    override def accept(t: Done): Unit = self ! Done
  })

  override def createReceive(): AbstractActor.Receive = {
    new AbstractActor.Receive(PartialFunction.empty[Any, BoxedUnit])
  }

  private def stageToDone(stage: CompletionStage[_]): CompletionStage[Done] =
    stage.thenApply(new java.util.function.Function[Any, Done.type]() {
      override def apply(t: Any): Done.type = Done
    })


  /**
   * Override shutdown to define your own shutdown process or wait for the sink to finish.
   * The default shutdown makes the following assumptions:<ol>
   *   <li>The stream materializes to a CompletionStage, or a Pair or List
   *       for which the last element is a CompletionStage</li>
   *   <li>This CompletionStage represents the state whether the stream is done</li>
   *   <li>The stream has the killSwitch as the first processing stage</li>
   * </ol>In which case you do not need to override this default shutdown if there are no further shutdown
   * requirements. In case you override shutdown, it is recommended that super.shutdown() be called
   * on overrides even if the stream only partially meets the requirements above.
   *
   * @return A CompletionStage[Done] that gets completed when the whole stream is done.
   */
  def shutdown(): CompletionStage[Done] = {
    matValue match {
      case f: CompletionStage[_] =>
        killSwitch.shutdown()
        stageToDone(f)
      case akka.japi.Pair(first, last) =>
        first match {
          case k: KillSwitch => k.shutdown()
          case _ =>
        }
        killSwitch.shutdown()
        last match {
          case f: CompletionStage[_] => stageToDone(f)
        }
      case l: java.util.List[_] if l.size > 0 =>
        l.get(0) match {
          case k: KillSwitch => k.shutdown()
          case _ =>
        }
        killSwitch.shutdown()
        l.get(l.size() - 1) match {
          case f: CompletionStage[_] => stageToDone(f)
          case _ => CompletableFuture.completedFuture(Done)
        }
      case _ =>
        killSwitch.shutdown()
        CompletableFuture.completedFuture(Done)
    }
  }

  /**
   * Override getStopTimeout to set a custom stop timeout.
   * @return The timeout, in milliseconds to allow for stopping the server.
   */
  def getStopTimeout: Long = super.stopTimeout.toMillis

  override final def stopTimeout: FiniteDuration = getStopTimeout.millis
}

/**
 * Java API for creating an HTTP FlowDefinition connecting to a PerpetualStream.
 */
abstract class FlowToPerpetualStream extends AbstractFlowDefinition {

  def matValue[T](perpetualStreamName: String): Sink[T, NotUsed] = {
    implicit val _ = context.system
    implicit val timeout: Timeout = Timeout(10 seconds)
    import akka.pattern.ask
    val responseF = SafeSelect(perpetualStreamName) ? MatValueRequest

    // Exception! This code is executed only at startup. We really need a better API, though.
    Await.result(responseF, timeout.duration).asInstanceOf[Sink[T, NotUsed]]
  }
}
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

package org.squbs.dispatcher

import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import org.apache.pekko.dispatch._
import com.typesafe.config.Config
import org.squbs.unicomplex.{ForkJoinPoolMXBean, JMX}

import scala.concurrent.{BlockContext, CanAwait}

class ForkJoinConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
  extends ExecutorServiceConfigurator(config, prerequisites) {

  def validate(t: ThreadFactory): ForkJoinPool.ForkJoinWorkerThreadFactory = t match {
    case correct: ForkJoinPool.ForkJoinWorkerThreadFactory => correct
    case x => throw new IllegalStateException(
      "The prerequisites for the ForkJoinExecutorConfigurator is a ForkJoinPool.ForkJoinWorkerThreadFactory!")
  }

  class ForkJoinExecutorServiceFactory(val jmxPrefix: String, val name: String,
                                       val threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory,
                                       val parallelism: Int) extends ExecutorServiceFactory {
    def createExecutorService: ExecutorService = {
      val pool =
        new ForkJoinPool(parallelism, threadFactory, AdaptedThreadFactory.doNothing, true) with ForkJoinPoolMXBean {
          def getMode: String = if (getAsyncMode) "Async" else "Sync"
        }
      import JMX._
      val prefix = if (jmxPrefix.isEmpty || jmxPrefix.endsWith(".")) jmxPrefix else jmxPrefix + '.'
      register(pool, prefix + forkJoinStatsName + name)
      pool
    }

  }

  final def createExecutorServiceFactory(id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = {

    val (tf, name) = threadFactory match {
      case m: MonitorableThreadFactory =>
        // add the dispatcher id to the thread names
        val name = m.name + "-" + id
        (AdaptedThreadFactory(m.withName(name)), name)
      case other => (other, id)
    }
    val fjConf = config.getConfig("fork-join-executor")
    import org.squbs.util.ConfigUtil._
    new ForkJoinExecutorServiceFactory(
      fjConf.get[String]("jmx-name-prefix", ""),
      name,
      validate(tf),
      ThreadPoolConfig.scaledPoolSize(
        fjConf.getInt("parallelism-min"),
        fjConf.getDouble("parallelism-factor"),
        fjConf.getInt("parallelism-max")))
  }
}

case class AdaptedThreadFactory(delegateFactory: MonitorableThreadFactory)
  extends ThreadFactory with ForkJoinPool.ForkJoinWorkerThreadFactory {

  import delegateFactory._

  def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
    val t = wire(new AdaptedThreadFactory.PekkoForkJoinWorkerThread(pool))
    // Name of the threads for the ForkJoinPool are not customizable. Change it here.
    t.setName(name + "-" + counter.incrementAndGet())
    t
  }

  def newThread(runnable: Runnable): Thread = delegateFactory.newThread(runnable)

  protected def wire[T <: Thread](t: T): T = {
    t.setUncaughtExceptionHandler(exceptionHandler)
    t.setDaemon(daemonic)
    contextClassLoader foreach t.setContextClassLoader
    t
  }

  // Hijack the counter from the Pekko MonitorableThreadFactory passed in.
  // This is only done once, so the cost should not be bad.
  protected val counter: AtomicLong = {
    val counterField = classOf[MonitorableThreadFactory].getDeclaredField("counter")
    counterField.setAccessible(true)
    counterField.get(delegateFactory).asInstanceOf[AtomicLong]
  }
}

object AdaptedThreadFactory {
  val doNothing: Thread.UncaughtExceptionHandler = new Thread.UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = ()
  }

  private[squbs] class PekkoForkJoinWorkerThread(_pool: ForkJoinPool)
    extends ForkJoinWorkerThread(_pool) with BlockContext {
    override def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      val result = new AtomicReference[Option[T]](None)
      ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker {
        def block(): Boolean = {
          result.set(Some(thunk))
          true
        }
        def isReleasable = result.get.isDefined
      })
      result.get.get // Exception intended if None
    }
  }
}

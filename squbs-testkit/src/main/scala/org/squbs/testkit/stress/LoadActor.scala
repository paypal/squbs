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

package org.squbs.testkit.stress

import java.lang.management.ManagementFactory
import javax.management.{Attribute, ObjectName}

import org.apache.pekko.actor.{Actor, ActorRef}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.math._

case class StartLoad(startTimeNs: Long, tps: Int, warmUp: FiniteDuration, steady: FiniteDuration)(submitFn: => Unit) {
  def this(startTimeNs: Long, tps: Int, warmUp: FiniteDuration, steady: FiniteDuration, submitFn: Runnable) =
    this(startTimeNs, tps, warmUp, steady){ submitFn.run() }
  private[stress] def invokeOnce() = submitFn
}

case class StartStats(startTimeNs: Long, warmUp: FiniteDuration, steady: FiniteDuration, interval: FiniteDuration)
case object Ping
case object GetStats
case class LoadStats(steadyTps: Double)
case class CPUStats(avg: Double, sDev: Double)

class LoadActor extends Actor {

  def receive = {
    case s: StartLoad =>
      context.become(runLoad(sender(), s))
  }

  def runLoad(requester: ActorRef, startMessage: StartLoad): Receive = {
    import context.dispatcher
    import startMessage._
    var requestsSoFar = 0L
    var steadyRequests = 0L
    var lastWakeUp = Long.MinValue

    val minInterval = 50.milliseconds
    val submitInterval = (1000000000 / tps).nanoseconds
    val firingInterval = if (submitInterval < minInterval) minInterval else submitInterval
    val steadyStart = startTimeNs + warmUp.toNanos
    val endTime = startTimeNs + warmUp.toNanos + steady.toNanos
    val firstFire = (startTimeNs + firingInterval.toNanos - System.nanoTime).nanoseconds
    val scheduler = context.system.scheduler.scheduleWithFixedDelay(firstFire, firingInterval, self, Ping)

    var nextIntervalStart = firingInterval.toNanos + startTimeNs

    def invoke(): Unit = {
      val wakeUpTime = System.nanoTime()
      if (wakeUpTime >= endTime) {
        scheduler.cancel()
        requester ! LoadStats(steadyRequests.toDouble / steady.toSeconds)
      }
      else {
        val expectedRequests = tps * (nextIntervalStart - startTimeNs) / 1000000000L
        val requests = {
          val r = (expectedRequests - requestsSoFar).toInt
          if (r > 0) r else 0
        }

        @tailrec
        def invoke(times: Int): Unit = {
          if (times > 0) {
            invokeOnce()
            invoke(times - 1)
          }
        }

        invoke(requests)

        requestsSoFar += requests
        if (wakeUpTime > steadyStart) steadyRequests += requests
        nextIntervalStart =
          if (lastWakeUp == Long.MinValue) wakeUpTime + firingInterval.toNanos
          else wakeUpTime + (wakeUpTime - lastWakeUp) // Predict next wake up from last interval
        lastWakeUp = wakeUpTime
      }
    }

    invoke()

    // Return the partial function dealing with the receive block.
    {
      case Ping => invoke()

      case GetStats =>
        val timeInSteady = System.nanoTime() - steadyStart
        if (timeInSteady < 0) sender() ! LoadStats(0d)
        else if (timeInSteady >= steady.toNanos) sender() ! LoadStats(steadyRequests.toDouble / steady.toSeconds)
        else sender() ! LoadStats(1000000000L * steadyRequests.toDouble / timeInSteady)
    }
  }
}

class CPUStatsActor extends Actor {

  def receive = {
    case s: StartStats => context.become(runStats(sender(), s))
  }

  def runStats(requester: ActorRef, startMessage: StartStats): Receive = {
    import context.dispatcher
    import startMessage._

    val cpu = new CPULoad
    val firstFire = (startTimeNs + warmUp.toNanos - System.nanoTime()).nanoseconds
    val endTime = startTimeNs + warmUp.toNanos + steady.toNanos
    val scheduler = context.system.scheduler.scheduleWithFixedDelay(firstFire, interval, self, Ping)

    {
      case Ping =>
        if (System.nanoTime >= endTime) {
          scheduler.cancel()
          requester ! CPUStats(cpu.average, cpu.standardDeviation)
        }
        else cpu.record()

      case GetStats => sender() ! CPUStats(cpu.average, cpu.standardDeviation)
    }
  }
}

class CPULoad {

  val mbs    = ManagementFactory.getPlatformMBeanServer
  val name    = ObjectName.getInstance("java.lang:type=OperatingSystem")

  var count = 0L
  var sum = 0d
  var sumSquares = 0d

  def current: Option[Double] = {
    val list = mbs.getAttributes(name, Array("ProcessCpuLoad"))

    if (list.isEmpty) None
    else {
      val att = list.get(0).asInstanceOf[Attribute]
      val value = att.getValue.asInstanceOf[java.lang.Double]

      if (value == -1.0) None // usually takes a couple of seconds before we get real values
      else Some(value) // returns the percentage
    }
  }

  def record(): Unit = {
    current foreach { value =>
      count += 1
      sum += value
      val sumSquares = if (count > 1) {
        val y = count * value - sum
        val s = this.sumSquares + y * y / (count * (count - 1))
        if (s >= 0) s else this.sumSquares
      } else this.sumSquares
      this.sumSquares = sumSquares
    }
  }

  def standardDeviation = if (count > 0) sqrt(sumSquares / count) else 0d

  def average = if (count > 0) sum / count else 0d
}

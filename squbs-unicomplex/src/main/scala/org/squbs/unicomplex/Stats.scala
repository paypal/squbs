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

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.annotation.tailrec
import scala.concurrent.duration._

/**
  * This is based on StatsSupport from Spray 1.3.1
  *
  * See https://github.com/spray/spray/blob/269ce885d3412e555237bb328aae89457f57c660/spray-can/src/main/scala/spray/can/server/StatsSupport.scala
  * See https://github.com/akka/akka/issues/17095
  */
private object StatsSupport {

  class StatsHolder {
    private val startTimeMillis = System.currentTimeMillis()
    // FIXME: Spray used "PaddedAtomicLong" here -- is that important?
    private val requestStarts = new AtomicLong
    private val responseStarts = new AtomicLong
    private val maxOpenRequests = new AtomicLong
    private val connectionsOpened = new AtomicLong
    private val connectionsClosed = new AtomicLong
    private val maxOpenConnections = new AtomicLong

    private def onConnectionStart(): Unit = {
      connectionsOpened.incrementAndGet()
      adjustMaxOpenConnections()
    }

    private def onConnectionEnd(): Unit = {
      connectionsClosed.incrementAndGet()
    }

    private def onRequestStart(): Unit = {
      requestStarts.incrementAndGet()
      adjustMaxOpenRequests()
    }

    private def onResponseStart(): Unit = {
      responseStarts.incrementAndGet()
    }

    @tailrec
    private def adjustMaxOpenConnections(): Unit = {
      val co = connectionsOpened.get
      val cc = connectionsClosed.get
      val moc = maxOpenConnections.get
      val currentMoc = co - cc
      if (currentMoc > moc)
        if (!maxOpenConnections.compareAndSet(moc, currentMoc)) adjustMaxOpenConnections()
    }

    @tailrec
    private def adjustMaxOpenRequests(): Unit = {
      val rqs = requestStarts.get
      val rss = responseStarts.get
      val mor = maxOpenRequests.get

      // FIXME: if a connection was aborted after we saw a request and before we
      // saw a response, then we will "leak" an apparently open request here...
      val currentMor = rqs - rss
      if (currentMor > mor)
        if (!maxOpenRequests.compareAndSet(mor, currentMor)) adjustMaxOpenRequests()
    }

    def toStats = Stats(
      uptime = FiniteDuration(System.currentTimeMillis() - startTimeMillis, TimeUnit.MILLISECONDS),
      totalRequests = requestStarts.get,
      openRequests = requestStarts.get - responseStarts.get,
      maxOpenRequests = maxOpenRequests.get,
      totalConnections = connectionsOpened.get,
      openConnections = connectionsOpened.get - connectionsClosed.get,
      maxOpenConnections = maxOpenConnections.get)

    def clear(): Unit = {
      requestStarts.set(0L)
      responseStarts.set(0L)
      maxOpenRequests.set(0L)
      connectionsOpened.set(0L)
      connectionsClosed.set(0L)
      maxOpenConnections.set(0L)
    }

    /**
      * Create a GraphStage which should be inserted into the connection flow
      * before the sealed route.
      *
      * This is also used to watch the connections.
      */
    def watchRequests() = new GraphStage[FlowShape[HttpRequest, HttpRequest]] {

        val in = Inlet[HttpRequest]("RequestCounter.in")
        val out = Outlet[HttpRequest]("RequestCounter.out")

        override val shape = FlowShape.of(in, out)

        override def createLogic(attr: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) {

            onConnectionStart()

            setHandler(in, new InHandler {
              override def onPush(): Unit = {
                onRequestStart()
                push(out, grab(in))
              }

              override def onUpstreamFailure(ex: Throwable): Unit = {
                onConnectionEnd()
                super.onUpstreamFailure(ex)
              }

              override def onUpstreamFinish(): Unit = {
                onConnectionEnd()
                super.onUpstreamFinish()
              }
            })

            setHandler(out, new OutHandler {
              override def onPull(): Unit = {
                pull(in)
              }
            })
        }
      }

    /**
      * Create a GraphStage which should be inserted into the connection flow
      * after the sealed route.
      *
      * Connections are not counted here.
      */
    def watchResponses() = new GraphStage[FlowShape[HttpResponse, HttpResponse]] {

        val in = Inlet[HttpResponse]("ResponseCounter.in")
        val out = Outlet[HttpResponse]("ResponseCounter.out")

        override val shape = FlowShape.of(in, out)

        override def createLogic(attr: Attributes): GraphStageLogic =
          new GraphStageLogic(shape) {

            setHandler(in, new InHandler {
              override def onPush(): Unit = {
                onResponseStart()
                push(out, grab(in))
              }
            })

            setHandler(out, new OutHandler {
              override def onPull(): Unit = {
                pull(in)
              }
            })
          }
      }
  }
}

/**
  * Note that 'requestTimeouts' is missing v.s. Spray 1.3
  *
  * Note that 'openRequests' may drift upwards over time due to aborted
  * connections!
  */
case class Stats(
                  uptime: FiniteDuration,
                  totalRequests: Long,
                  openRequests: Long,
                  maxOpenRequests: Long,
                  totalConnections: Long,
                  openConnections: Long,
                  maxOpenConnections: Long)

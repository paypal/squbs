/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.unicomplex

import org.squbs.unicomplex.StatsSupport.StatsHolder


class ServerStats(name: String, statsHolder: StatsHolder) extends ServerStatsMXBean {

  val df = new java.text.SimpleDateFormat("HH:mm:ss.SSS")

  override def getListenerName: String = name

  override def getTotalConnections: Long = status.map(_.totalConnections) getOrElse -1

  override def getRequestsTimedOut: Long = -1 //status.map(_.requestTimeouts) getOrElse -1

  override def getOpenRequests: Long = status.map(_.openRequests) getOrElse -1

  override def getUptime: String = status.map(s => df.format(s.uptime.toMillis)) getOrElse "00:00:00.000"

  override def getMaxOpenRequests: Long = status.map(_.maxOpenRequests) getOrElse -1

  override def getOpenConnections: Long = status.map(_.openConnections) getOrElse -1

  override def getMaxOpenConnections: Long = status.map(_.maxOpenConnections) getOrElse -1

  override def getTotalRequests: Long = status.map(_.totalRequests) getOrElse -1

  private def status: Option[Stats] = {
    Option(statsHolder.toStats)
  }
}

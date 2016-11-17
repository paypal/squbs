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
package org.squbs.pattern.stream

import java.io.File

import com.typesafe.config.ConfigException.BadValue
import com.typesafe.config.{Config, ConfigMemorySize}
import net.openhft.chronicle.queue.{RollCycle, RollCycles}
import net.openhft.chronicle.wire.WireType
import org.squbs.util.ConfigUtil._

object QueueConfig {

  val defaultCycle: RollCycle = RollCycles.DAILY
  val defaultWireType: WireType = WireType.BINARY
  val defaultBlockSize: Long = 64L << 20
  val defaultOutputPort: Int = 1
  val defaultCommitOrderPolicy = Lenient

  def from(config: Config): QueueConfig = {
    val persistDir = new File(config getString "persist-dir")
    val cycle = config.getOption[String]("roll-cycle") map {
      s => RollCycles.valueOf(s.toUpperCase)
    } getOrElse defaultCycle
    val wireType = config.getOption[String]("wire-type") map {
      s => WireType.valueOf(s.toUpperCase)
    } getOrElse defaultWireType
    val blockSize = config.getOption[ConfigMemorySize]("block-size") map (_.toBytes) getOrElse defaultBlockSize
    val indexSpacing = config.getOption[ConfigMemorySize]("index-spacing").
      map(_.toBytes.toInt).getOrElse(cycle.defaultIndexSpacing)
    val indexCount = config.get[Int]("index-count", cycle.defaultIndexCount)
    val outputPorts = config.get[Int]("output-ports", defaultOutputPort)
    val commitOrder = config.getOption[String]("commit-order-policy") map { s =>
      if(s == "strict") Strict
      else if(s == "lenient") Lenient
      else throw new BadValue("commit-order-policy", "Allowed values: strict or lenient")
    } getOrElse defaultCommitOrderPolicy
    QueueConfig(
      persistDir,
      cycle,
      wireType,
      blockSize,
      indexSpacing,
      indexCount,
      outputPorts = outputPorts,
      commitOrderPolicy = commitOrder)
  }
}

case class QueueConfig(persistDir: File,
                       rollCycle: RollCycle = QueueConfig.defaultCycle,
                       wireType: WireType = QueueConfig.defaultWireType,
                       blockSize: Long = QueueConfig.defaultBlockSize,
                       indexSpacing: Int = QueueConfig.defaultCycle.defaultIndexSpacing,
                       indexCount: Int = QueueConfig.defaultCycle.defaultIndexCount,
                       isBuffered: Boolean = false,
                       epoch: Long = 0L,
                       outputPorts: Int = QueueConfig.defaultOutputPort,
                       commitOrderPolicy: CommitOrderPolicy = QueueConfig.defaultCommitOrderPolicy)

sealed trait CommitOrderPolicy
object Strict extends CommitOrderPolicy
object Lenient extends CommitOrderPolicy
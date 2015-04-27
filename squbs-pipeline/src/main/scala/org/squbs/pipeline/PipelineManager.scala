/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.pipeline

import akka.actor._
import akka.agent.Agent
import com.typesafe.config.{Config, ConfigObject}
import com.typesafe.scalalogging.LazyLogging
import org.squbs.pipeline.ConfigHelper._

import scala.collection.JavaConversions._
import scala.collection.mutable

case class PipelineManager(configs: Map[String, ProxyConfig], processors: Agent[Map[String, Processor]]) extends Extension with LazyLogging {

  def default(implicit actorRefFactory: ActorRefFactory) = {
    get("default-proxy")
  }

  def get(name: String)(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    processors().get(name) match {
      case None =>
        configs.get(name) match {
          case Some(cfg) =>
            try {
              val processor = Class.forName(cfg.factoryClazz).newInstance().asInstanceOf[ProcessorFactory].create(cfg.settings)
              processors.send {
                currentMap =>
                  currentMap.get(name) match {
                    case Some(ref) => currentMap
                    case None =>
                      val proc = processor.getOrElse(null)
                      currentMap + (name -> proc) ++ cfg.aliases.map(_ -> proc)
                  }
              }
              processor
            } catch {
              case t: Throwable =>
                logger.error(s"Can't instantiate squbs.proxy with name of $name and factory class name of ${cfg.factoryClazz}", t)
                throw t
            }
          case None if (name.equals("default-proxy")) =>
            processors.send {
              currentMap =>
                currentMap.get(name) match {
                  case Some(ref) => currentMap
                  case None => currentMap + (name -> null)
                }
            }
            None
          case _ => throw new IllegalArgumentException(s"No registered squbs.proxy found with name of $name")
        }
      case Some(null) => None
      case other => other
    }

  }

}

case class ProxyConfig(name: String,
                       aliases: Seq[String],
                       factoryClazz: String,
                       settings: Option[Config])

object PipelineManager extends ExtensionId[PipelineManager] with ExtensionIdProvider with LazyLogging {

  override def createExtension(system: ExtendedActorSystem): PipelineManager = PipelineManager.create(system)

  override def lookup(): ExtensionId[_ <: Extension] = PipelineManager

  private def create(system: ActorSystem): PipelineManager = {
    val config = system.settings.config
    val allMatches = config.root.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.getOptionalString("type") == Some("squbs.proxy") => (n, v.toConfig)
    }
    import system.dispatcher
    PipelineManager(genConfigs(allMatches), Agent(Map.empty[String, Processor]))
  }

  private def genConfigs(configs: Seq[(String, Config)]): Map[String, ProxyConfig] = {
    val proxyMap = mutable.Map.empty[String, ProxyConfig]
    configs.foreach {
      conf =>
        try {
          val subCfg = conf._2
          val key = conf._1
          val aliasNames = subCfg.getOptionalStringList("aliases") getOrElse Seq.empty[String]
          val proxyConf = ProxyConfig(key, aliasNames, subCfg.getString("processorFactory"), subCfg.getOptionalConfig("settings"))
          key +: aliasNames foreach { name =>
            proxyMap.get(name) match {
              case None => proxyMap.put(name, proxyConf)
              case Some(value) => throw new IllegalArgumentException("Proxy name is already used by proxy: " + value.name)
            }
          }

        } catch {
          case t: Throwable =>
            logger.error("Error in parsing squbs proxy setting", t)
            throw t
        }
    }
    proxyMap.toMap
  }
}




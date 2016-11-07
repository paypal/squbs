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

package org.squbs.pipeline

import akka.actor._
import akka.agent.Agent
import com.typesafe.config.{Config, ConfigObject}
import com.typesafe.scalalogging.LazyLogging
import org.squbs.pipeline.ConfigHelper._

import scala.collection.JavaConversions._
import scala.collection.mutable

case class PipelineHandlerManager(configs: Map[String, HandlerConfig], handlers: Agent[Map[String, Option[Handler]]])
    extends Extension with LazyLogging {

  def get(name: String)(implicit actorRefFactory: ActorRefFactory): Option[Handler] = {
    handlers().get(name) match {
      case None =>
        configs.get(name) match {
          case None => throw new IllegalArgumentException(s"No registered handler found with name of $name")
          case Some(cfg) =>
            try {
              val handler = Class.forName(cfg.factoryClazz).asSubclass(classOf[HandlerFactory]).newInstance().
                create(cfg.settings)
              handlers.send {
                currentMap =>
                  currentMap.get(name) match {
                    case Some(ref) => currentMap
                    case None => currentMap + (name -> handler)
                  }
              }
              handler
            } catch {
              case t: Throwable =>
                logger.error(s"Can't instantiate the handler with name of $name and factory class name of ${cfg.factoryClazz}", t)
                throw t
            }
        }
      case Some(other) => other
    }

  }

}

case class HandlerConfig(name: String,
                         factoryClazz: String,
                         settings: Option[Config])

object PipelineHandlerManager extends ExtensionId[PipelineHandlerManager] with ExtensionIdProvider with LazyLogging {

  override def createExtension(system: ExtendedActorSystem): PipelineHandlerManager = PipelineHandlerManager.create(system)

  override def lookup(): ExtensionId[_ <: Extension] = PipelineHandlerManager

  private def create(system: ActorSystem): PipelineHandlerManager = {
    val config = system.settings.config
    val allMatches = config.root.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.getOptionalString("type").contains("pipeline.handler") => (n, v.toConfig)
    }
    import system.dispatcher
    PipelineHandlerManager(genConfigs(allMatches), Agent(Map.empty[String, Option[Handler]]))
  }

  private def genConfigs(configs: Seq[(String, Config)]): Map[String, HandlerConfig] = {
    val handlerMap = mutable.Map.empty[String, HandlerConfig]
    configs.foreach {
      conf =>
        try {
          val subCfg = conf._2
          val name = conf._1
          handlerMap.put(name, HandlerConfig(name, subCfg.getString("factory"), subCfg.getOptionalConfig("settings")))
        } catch {
          case t: Throwable =>
            logger.error("Error in parsing pipeline handler setting", t)
            throw t
        }
    }
    handlerMap.toMap
  }
}




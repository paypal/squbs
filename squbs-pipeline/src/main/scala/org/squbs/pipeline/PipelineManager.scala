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


case class PipelineManager(configs: Map[String, RawPipelineSetting], pipelines: Agent[Map[String, Either[Option[Processor], PipelineSetting]]]) extends Extension with LazyLogging {

  def default(implicit actorRefFactory: ActorRefFactory) : Option[Processor] = getProcessor("default-proxy")


  def getProcessor(name: String)(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    get(name) match {
      case Some(Left(v)) => v
      case Some(Right(v)) => v.factory.create(v.pipelineConfig, v.setting)
      case None if name.equals("default-proxy") => None
      case None => throw new IllegalArgumentException(s"No registered squbs.proxy found with name: $name")
    }
  }

  def getPipelineSetting(name: String)(implicit actorRefFactory: ActorRefFactory): Option[PipelineSetting] = {
    get(name) match {
      case Some(Right(v)) => Option(v)
      case Some(Left(v)) => throw new IllegalArgumentException(s"squbs.proxy found with name of $name, but the factory should implement PipelineProcessorFactory")
      case None => throw new IllegalArgumentException(s"No registered pipeline found with name: $name")
    }

  }

  private def get(name: String)(implicit actorRefFactory: ActorRefFactory): Option[Either[Option[Processor], PipelineSetting]] = {
    pipelines().get(name) match {
      case None =>
        configs.get(name) match {
          case Some(cfg) =>
            try {
              val factoryInstance = Class.forName(cfg.factoryClass).newInstance()
              val entry: Either[Option[Processor], PipelineSetting] = factoryInstance match {
                case f : PipelineProcessorFactory =>
                  val pipelineConfig = cfg.settings.fold(SimplePipelineConfig.empty)(SimplePipelineConfig(_))
                  Right(PipelineSetting(f, Option(pipelineConfig), cfg.settings))
                case f : ProcessorFactory =>
                  Left(f.create(cfg.settings))
                case f => throw new IllegalArgumentException(s"Unsupported processor factory: ${cfg.factoryClass}")
              }
              pipelines.send {
                currentMap =>
                  currentMap.get(name) match {
                    case Some(ref) => currentMap
                    case None => currentMap + (name -> entry) ++ cfg.aliases.map(_ -> entry)
                  }
              }
              Option(entry)
            } catch {
              case t: Throwable =>
                logger.error(s"Can't instantiate squbs.proxy with name of $name and factory class name of ${cfg.factoryClass}", t)
                throw t
            }
          case None => None
        }
      case other => other
    }

  }

}

case class RawPipelineSetting(name: String,
                              aliases: Seq[String],
                              factoryClass: String,
                              settings: Option[Config])

case class PipelineSetting(factory: PipelineProcessorFactory = SimplePipelineResolver,
                           config: Option[SimplePipelineConfig] = None,
                           setting: Option[Config] = None) {
  def pipelineConfig: SimplePipelineConfig = config.getOrElse(SimplePipelineConfig.empty)

}

object PipelineSetting {

  val default = PipelineSetting()

  def apply(resolver: PipelineProcessorFactory, config: SimplePipelineConfig): PipelineSetting = PipelineSetting(resolver, Option(config))

  def apply(resolver: PipelineProcessorFactory, config: SimplePipelineConfig, setting: Config): PipelineSetting = PipelineSetting(resolver, Option(config), Option(setting))
}


object PipelineManager extends ExtensionId[PipelineManager] with ExtensionIdProvider with LazyLogging {

  override def createExtension(system: ExtendedActorSystem): PipelineManager = PipelineManager.create(system)

  override def lookup(): ExtensionId[_ <: Extension] = PipelineManager

  private def create(system: ActorSystem): PipelineManager = {
    val config = system.settings.config
    val allMatches = config.root.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.getOptionalString("type") == Some("squbs.proxy") => (n, v.toConfig)
    }
    import system.dispatcher
    PipelineManager(genConfigs(allMatches), Agent(Map.empty[String, Either[Option[Processor], PipelineSetting]]))
  }

  private def genConfigs(configs: Seq[(String, Config)]): Map[String, RawPipelineSetting] = {
    val proxyMap = mutable.Map.empty[String, RawPipelineSetting]
    configs.foreach {
      conf =>
        try {
          val subCfg = conf._2
          val key = conf._1
          val aliasNames = subCfg.getOptionalStringList("aliases") getOrElse Seq.empty[String]
          val factoryClass = subCfg.getOptionalString("processorFactory") match {
            case Some(text) => text
            case None => subCfg.getString("factory")
          }
          val proxyConf = RawPipelineSetting(key, aliasNames, factoryClass, subCfg.getOptionalConfig("settings"))
          key +: aliasNames foreach { name =>
            proxyMap.get(name) match {
              case None => proxyMap += (name -> proxyConf)
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




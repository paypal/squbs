package org.squbs.proxy

import akka.actor._
import akka.agent.Agent
import com.typesafe.config.{Config, ConfigObject}
import com.typesafe.scalalogging.LazyLogging
import org.squbs.unicomplex.ConfigUtil._

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
 * Created by lma on 7/30/2015.
 */
case class PipelineRegistry(configs: Map[String, RawPipelineSetting], settings: Agent[Map[String, PipelineSetting]]) extends Extension with LazyLogging {

  def get(name: String)(implicit actorRefFactory: ActorRefFactory): Option[PipelineSetting] = {
    settings().get(name) match {
      case None =>
        configs.get(name) match {
          case Some(cfg) =>
            try {
              val resolver = cfg.resolverClazz.map(Class.forName(_).newInstance().asInstanceOf[PipelineResolver])
              val pipelineConf = cfg.pipeConfig.map(SimplePipelineConfig(_))
              val setting = PipelineSetting(resolver, pipelineConf, cfg.setting)
              settings.send {
                currentMap =>
                  currentMap.get(name) match {
                    case Some(ref) => currentMap
                    case None => currentMap + (name -> setting) ++ cfg.aliases.map(_ -> setting)
                  }
              }
              Some(setting)
            } catch {
              case t: Throwable =>
                logger.error(s"Can't instantiate squbs.pipe with name of $name and resolver class name of ${cfg.resolverClazz.getOrElse("empty")}", t)
                throw t
            }
          case _ => None
        }
      case other => other
    }

  }

}

object PipelineRegistry extends ExtensionId[PipelineRegistry] with ExtensionIdProvider with LazyLogging {

  override def createExtension(system: ExtendedActorSystem): PipelineRegistry = PipelineRegistry.create(system)

  override def lookup(): ExtensionId[_ <: Extension] = PipelineRegistry

  private def create(system: ActorSystem): PipelineRegistry = {
    val config = system.settings.config
    val allMatches = config.root.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.getOptionalString("type") == Some("squbs.pipe") => (n, v.toConfig)
    }
    import system.dispatcher
    PipelineRegistry(genConfigs(allMatches), Agent(Map.empty[String, PipelineSetting]))
  }

  private def genConfigs(configs: Seq[(String, Config)]): Map[String, RawPipelineSetting] = {
    val pipeMap = mutable.Map.empty[String, RawPipelineSetting]
    configs.foreach {
      conf =>
        try {
          val subCfg = conf._2
          val key = conf._1
          val aliasNames = subCfg.getOptionalStringList("aliases") getOrElse Seq.empty[String]
          val resolverClazz = subCfg.getOptionalString("resolver")
          val setting = subCfg.getOptionalConfig("settings")
          val pipe = subCfg.getOptionalConfig("pipeline")
          if (resolverClazz.isEmpty && pipe.isEmpty) throw new IllegalArgumentException("Must have resolver or pipeline defined!")
          val pipeRawSetting = RawPipelineSetting(key, aliasNames, resolverClazz, pipe, setting)
          key +: aliasNames foreach { name =>
            pipeMap.get(name) match {
              case None => pipeMap.put(name, pipeRawSetting)
              case Some(value) => throw new IllegalArgumentException("Pipeline name is already used by pipeline: " + value.name)
            }
          }

        } catch {
          case t: Throwable =>
            logger.error("Error in parsing squbs pipeline setting", t)
            throw t
        }
    }
    pipeMap.toMap
  }
}

case class RawPipelineSetting(name: String,
                              aliases: Seq[String],
                              resolverClazz: Option[String],
                              pipeConfig: Option[Config],
                              setting: Option[Config])

case class PipelineSetting(resolver: Option[PipelineResolver] = None,
                           config: Option[SimplePipelineConfig] = None,
                           setting: Option[Config] = None) {
  def pipelineConfig: SimplePipelineConfig = config.getOrElse(SimplePipelineConfig.empty)
}

object PipelineSetting {
  val empty = PipelineSetting()

  def apply(resolver : PipelineResolver, config : SimplePipelineConfig) : PipelineSetting = PipelineSetting(Option(resolver), Option(config))

  def apply(resolver : PipelineResolver, config : SimplePipelineConfig, setting : Config) : PipelineSetting = PipelineSetting(Option(resolver), Option(config), Option(setting))
}

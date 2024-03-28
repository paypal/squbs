/*
 * Copyright 2018 PayPal
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

package org.squbs.pipeline

import org.apache.pekko.NotUsed
import org.apache.pekko.actor._
import org.apache.pekko.stream.{javadsl, scaladsl}
import com.typesafe.config.ConfigObject

import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait PipelineType
case object ServerPipeline extends PipelineType
case object ClientPipeline extends PipelineType

case class Context(name: String, pipelineType: PipelineType)

package japi {

  import org.apache.pekko.stream.javadsl.BidiFlow

  /**
    * Java API
    */
  abstract class PipelineFlowFactory {

    def create(context: Context, system: ActorSystem):
    javadsl.BidiFlow[RequestContext, RequestContext, RequestContext, RequestContext, NotUsed]

    def abortable(flow: BidiFlow[RequestContext, RequestContext, RequestContext, RequestContext, NotUsed]):
    javadsl.BidiFlow[RequestContext, RequestContext, RequestContext, RequestContext, NotUsed] =
      AbortableBidiFlow(flow.asScala).abortable.asJava
  }

}

trait PipelineFlowFactory extends japi.PipelineFlowFactory {

  def create(context: Context)(implicit system: ActorSystem):
  scaladsl.BidiFlow[RequestContext, RequestContext, RequestContext, RequestContext, NotUsed]

  override def create(context: Context, system: ActorSystem):
  javadsl.BidiFlow[RequestContext, RequestContext, RequestContext, RequestContext, NotUsed] = create(context)(system).asJava
}

class PipelineExtensionImpl(flowFactoryMap: Map[String, japi.PipelineFlowFactory],
                            serverDefaultFlows: (Option[String], Option[String]),
                            clientDefaultFlows: (Option[String], Option[String]))
                           (implicit system: ActorSystem) extends Extension {

  def getFlow(pipelineSetting: PipelineSetting, context: Context): Option[PipelineFlow] = {

    val (appFlow, defaultsOn) = pipelineSetting

    val (defaultPreFlow, defaultPostFlow) =
      if (defaultsOn getOrElse true) {
        context.pipelineType match {
          case ServerPipeline => serverDefaultFlows
          case ClientPipeline => clientDefaultFlows
        }
      } else (None, None)

    val pipelineFlowNames = (defaultPreFlow :: appFlow :: defaultPostFlow :: Nil).flatten
    buildPipeline(pipelineFlowNames, context)
  }

  private def buildPipeline(flowNames: Seq[String], context: Context) = {

    val flows = flowNames map { case name =>
      val flowFactory = flowFactoryMap
        .getOrElse(name, throw new IllegalArgumentException(s"Invalid pipeline name $name"))

      flowFactory match {
        case factory: PipelineFlowFactory => factory.create(context)
        case factory: japi.PipelineFlowFactory => factory.create(context, system).asScala
        case factory => throw new IllegalArgumentException(s"Unsupported flow factory type ${factory.getClass.getName}")
      }
    }
    flows.reduceLeftOption(_ atop _)
  }
}

object PipelineExtension extends ExtensionId[PipelineExtensionImpl] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): PipelineExtensionImpl = {

    val config = system.settings.config

    val flows = config.root.asScala.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.hasPath("type") && v.toConfig.getString("type") == "squbs.pipelineflow" =>
        (n, v.toConfig)
    }

    var flowMap = Map.empty[String, japi.PipelineFlowFactory]
    flows foreach { case (name, config) =>
      val factoryClassName = config.getString("factory")

      val flowFactory = Class.forName(factoryClassName).newInstance().asInstanceOf[japi.PipelineFlowFactory]

      flowMap = flowMap + (name -> flowFactory)
    }

    val serverDefaultPreFlow = Try(config.getString("squbs.pipeline.server.default.pre-flow")).toOption
    val serverDefaultPostFlow = Try(config.getString("squbs.pipeline.server.default.post-flow")).toOption
    val clientDefaultPreFlow = Try(config.getString("squbs.pipeline.client.default.pre-flow")).toOption
    val clientDefaultPostFlow = Try(config.getString("squbs.pipeline.client.default.post-flow")).toOption

    new PipelineExtensionImpl(
      flowMap,
      (serverDefaultPreFlow, serverDefaultPostFlow),
      (clientDefaultPreFlow, clientDefaultPostFlow))(system)
  }

  override def lookup: ExtensionId[_ <: Extension] = PipelineExtension

  /**
    * Java API: retrieve the Pipeline extension for the given system.
    */
  override def get(system: ActorSystem): PipelineExtensionImpl = super.get(system)
}

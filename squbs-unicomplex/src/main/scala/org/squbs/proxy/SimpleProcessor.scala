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
package org.squbs.proxy

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.squbs.pipeline._
import org.squbs.unicomplex.ConfigUtil._

import scala.concurrent.{ExecutionContext, Future}

case class SimplePipelineConfig(reqPipe: Seq[_ <: Handler], respPipe: Seq[_ <: Handler])

case class SimpleProcessor(pipeConf: SimplePipelineConfig) extends Processor {
  //inbound processing
  def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
    pipeConf.reqPipe.foldLeft(Future.successful(reqCtx)) {
      (ctxFuture, handler) =>
        ctxFuture flatMap {
          ctx =>
            if (ctx.responseReady) Future.successful(ctx) //bypass all subsequent handlers
            else handler.process(ctx)
        }
    }
  }

  //outbound processing
  def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
    pipeConf.respPipe.foldLeft(Future.successful(reqCtx)) { (ctxFuture, handler) => ctxFuture.flatMap(handler.process(_))}
  }
}

object SimpleProcessor {
  def empty: SimpleProcessor = new SimpleProcessor(SimplePipelineConfig.empty)
}

class SimpleProcessorFactory extends ProcessorFactory {
  def create(settings: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    settings match {
      case Some(conf) => Some(SimpleProcessor(SimplePipelineConfig(conf)))
      case _ => None
    }
  }
}

object SimplePipelineConfig extends LazyLogging {

  def apply(config: Config)(implicit actorRefFactory: ActorRefFactory): SimplePipelineConfig = {

    val system: Option[ActorSystem] = actorRefFactory match {
      case system: ActorSystem => Some(system)
      case ctx: ActorContext => Some(ctx.system)
      case other =>
        logger.error(s"Unsupported actorRefFactory: ${other.getClass.getName}")
        None
    }
    system.fold(SimplePipelineConfig.empty) {
      sys =>
        val mgr = PipelineHandlerManager(sys)
        val reqPipe = config.getOptionalStringList("inbound").getOrElse(Seq.empty)
        val respPipe = config.getOptionalStringList("outbound").getOrElse(Seq.empty)
        val reqPipeObj = reqPipe.flatMap { h =>
          mgr.get(h)
        }
        val respPipeObj = respPipe.flatMap { h =>
          mgr.get(h)
        }
        SimplePipelineConfig(reqPipeObj, respPipeObj)
    }
  }

  /**
   * Java API
   */
  def create(config: Config)(implicit actorRefFactory: ActorRefFactory): SimplePipelineConfig = {
    apply(config)
  }

  /**
   * Java API
   */
  def create(reqPipe: java.util.List[_ <: Handler], respPipe: java.util.List[_ <: Handler]): SimplePipelineConfig = {
    import scala.collection.JavaConversions._;
    SimplePipelineConfig(reqPipe, respPipe)
  }


  val empty = SimplePipelineConfig(Seq.empty, Seq.empty)
}

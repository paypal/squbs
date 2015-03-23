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

import akka.actor.{ActorContext, ActorRefFactory}
import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
import org.slf4j.LoggerFactory
import org.squbs.pipeline._
import scala.collection.JavaConversions._
import scala.collection.mutable.{HashMap => HMap}
import scala.concurrent.{ExecutionContext, Future}

case class SimplePipelineConfig(reqPipe: Seq[_ <: Handler], respPipe: Seq[_ <: Handler])

case class SimpleProcessor(pipeConf: SimplePipelineConfig) extends Processor {
	//inbound processing
	def inbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
		pipeConf.reqPipe.foldLeft(Future.successful(reqCtx)) { (ctx, handler) => ctx flatMap (handler.process(_))}
	}

	//outbound processing
	def outbound(reqCtx: RequestContext)(implicit executor: ExecutionContext, context: ActorContext): Future[RequestContext] = {
		pipeConf.respPipe.foldLeft(Future.successful(reqCtx)) { (ctx, handler) => ctx.flatMap(handler.process(_))}
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

object SimplePipelineConfig {
	private val log = LoggerFactory.getLogger(this.getClass)

	def apply(config: Config): SimplePipelineConfig = {
		import org.squbs.unicomplex.ConfigUtil._

		val handlerConf = config.getOptionalConfig("handlers").getOrElse(ConfigFactory.empty)
		val handlerCache = new HMap[String, Handler]()
		handlerConf.root.foreach {
			case (name, hconf: ConfigValue) =>
				val clazz = hconf.unwrapped.toString
				try {
					handlerCache += (name -> Class.forName(clazz).newInstance.asInstanceOf[Handler])
				} catch {
					case t: Throwable =>
						log.error("Can't instantiate the handler with name of:" + name)
				}
			case _ => // ignore
		}

		val reqPipe = config.getOptionalStringList("inbound").getOrElse(Seq.empty)
		val respPipe = config.getOptionalStringList("outbound").getOrElse(Seq.empty)

		val reqPipeObj = reqPipe.flatMap { h =>
			handlerCache.get(h)
		}

		val respPipeObj = respPipe.flatMap { h =>
			handlerCache.get(h)
		}

		SimplePipelineConfig(reqPipeObj, respPipeObj)
	}

	def empty = SimplePipelineConfig(Seq.empty, Seq.empty)
}

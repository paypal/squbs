package org.squbs.proxy

import akka.actor.{ Actor, ActorLogging}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}
import org.squbs.unicomplex.ConfigUtil._

import scala.collection.JavaConversions._
import scala.collection.mutable.{HashMap => HMap}

/**
 * Created by jiamzhang on 15/2/14.
 */
case class PipeLineConfig(handlers: Seq[_ <: Handler], tags: Map[String, String]) {
	def fit(ctx: RequestContext): Boolean = {
		tags.foldLeft[Boolean](false) { (l, r) =>
			l || (ctx.attribute[String](r._1) match {
				case Some(v) => v == r._2
				case None => false
			})
		}
	}
}

case class PipeConfigInfo(reqConf: Seq[PipeLineConfig], respConf: Seq[PipeLineConfig])

class PipeConfigLoader extends Actor with ActorLogging {

	override def receive = {
		case conf: Config =>
			val responder = sender()
			val handlerConf = conf.getOptionalConfig("handlers").getOrElse(ConfigFactory.empty)
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

			val pipeConf = conf.getOptionalConfig("pipelines").getOrElse(ConfigFactory.empty)
			val pipeCache = new HMap[String, (String, PipeLineConfig)]()
			pipeConf.root.foreach {
				case (name, confObj: ConfigObject) =>
					val pipeType = confObj.toConfig.getOptionalString("type").getOrElse("common").toLowerCase
					val pipeHandlers = confObj.toConfig.getOptionalStringList("handlers").getOrElse(Seq.empty[String])
					val pipeTags = confObj.toConfig.getOptionalStringList("tags").getOrElse(Seq.empty[String])

					pipeCache += (name ->(pipeType, buildPipeLineConfig(handlerCache, pipeType, pipeHandlers, pipeTags)))
			}
			val reqPipe = pipeConf.getOptionalStringList("request").getOrElse(Seq("*"))
			val respPipe = pipeConf.getOptionalStringList("response").getOrElse(Seq("*"))

			val reqPipeObj = reqPipe.flatMap {
				case "*" =>
					pipeCache.flatMap {
						case (_, ("request", reqConf)) => Some(reqConf)
						case _ => None
					}.toSeq
				case n if n != "*" => pipeCache.get(n).map{ case (t, pconf) => pconf }
			}

			val respPipeObj = respPipe.flatMap {
				case "*" => pipeCache.flatMap {
					case (_, ("response", respConf)) => Some(respConf)
					case _ => None
				}
				case n if n != "*" => pipeCache.get(n).map { case (t, pconf) => pconf }
			}

			responder ! PipeConfigInfo(reqPipeObj, respPipeObj)
	}

	private def buildPipeLineConfig(handlerCache: HMap[String, Handler],
	                                pipeType: String,
	                                handlers: Seq[String],
	                                tags: Seq[String]): PipeLineConfig = {
		val handlerObjs = handlers.flatMap(handlerCache.get(_)).map { h =>
			pipeType match {
				case "request" => h.asInstanceOf[RequestHandler]
				case "response" => h.asInstanceOf[ResponseHandler]
				case _ => h
			}
		}

		val tagObjs = tags.flatMap { entry =>
			val ary = entry.split(":")
			Some((ary(0) -> ary(1)))
		}.toMap

		PipeLineConfig(handlerObjs, tagObjs)
	}
}

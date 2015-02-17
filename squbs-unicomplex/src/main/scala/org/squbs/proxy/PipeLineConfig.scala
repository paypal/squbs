package org.squbs.proxy

import akka.actor.{ Actor, ActorLogging}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}
import org.squbs.unicomplex.ConfigUtil._

import scala.collection.JavaConversions._
import scala.collection.mutable.{HashMap => HMap}
import scala.util.matching.Regex

/**
 * Created by jiamzhang on 15/2/14.
 */
case class PipeLineFilter(headers: Map[String, String] = Map.empty, entity: Option[Regex] = None, uri: Option[Regex] = None, status: Option[Regex] = None, method: Option[Regex] = None)
case object PipeLineFilter {
	def empty = PipeLineFilter()
}
case class PipeLineConfig(handlers: Seq[_ <: Handler], filter: PipeLineFilter) {
	def fitRequest(ctx: RequestContext): Boolean = {
		if (filter == PipeLineFilter.empty) true
		else {
			val hboolean = filter.headers.foldLeft[Boolean](true) { (l, r) =>
				l && (ctx.request.headers.find(_.name == r._1) match {
					case Some(h) => h.value == r._2
					case None => false
				})
			}

			val pboolean = filter.uri match {
				case Some(u) => u.pattern.matcher(ctx.request.uri.toRelative.toString).matches()
				case None => true
			}

			val eboolean = filter.entity match {
				case Some(r) => r.pattern.matcher(ctx.request.entity.asString).matches()
				case None => true
			}

			val mboolean = filter.method match {
				case Some(r) => r.pattern.matcher(ctx.request.method.name.toLowerCase).matches()
				case None => true
			}
			hboolean && pboolean && eboolean && mboolean
		}
	}

	def fitResponse(ctx: RequestContext): Boolean = {
		if (filter == PipeLineFilter.empty) true
		else {
			val resp = ctx.response match {
				case NormalResponse(resp) => resp
				case e: ExceptionalResponse => e.response
			}

			val hboolean = filter.headers.foldLeft[Boolean](true) { (l, r) =>
				l && (resp.headers.find(_.name == r._1) match {
					case Some(h) => h.value == r._2
					case None => false
				})
			}

			val eboolean = filter.entity match {
				case Some(r) => r.pattern.matcher(resp.entity.asString).matches()
				case None => true
			}

			val sboolean = filter.status match {
				case Some(r) => r.pattern.matcher(resp.status.intValue.toString).matches()
				case None => true
			}
			hboolean && eboolean && sboolean
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
			val pipeCache = new HMap[String, PipeLineConfig]()
			pipeConf.root.foreach {
				case (name, confObj: ConfigObject) =>
					val handlersConf = confObj.toConfig.getOptionalStringList("handlers").getOrElse(Seq.empty[String])
					val pipeHandlers = handlersConf.flatMap(handlerCache.get(_))

					val filterConf = confObj.toConfig.getOptionalConfig("filter").getOrElse(ConfigFactory.empty)
					val pipeFilter = buildFilter(filterConf)

					pipeCache += (name -> PipeLineConfig(pipeHandlers, pipeFilter))
				case _ => //ignore
			}

			val reqPipe = pipeConf.getOptionalStringList("request").getOrElse(Seq("*"))
			val respPipe = pipeConf.getOptionalStringList("response").getOrElse(Seq("*"))

			val reqPipeObj = reqPipe.flatMap {
				case "*" =>
					pipeCache.flatMap {
						case (_, reqConf) => Some(reqConf)
						case _ => None
					}.toSeq
				case n if n != "*" => pipeCache.get(n)
			}

			val respPipeObj = respPipe.flatMap {
				case "*" => pipeCache.flatMap {
					case (_, respConf) => Some(respConf)
					case _ => None
				}
				case n if n != "*" => pipeCache.get(n)
			}

			responder ! PipeConfigInfo(reqPipeObj, respPipeObj)
	}

	private def buildFilter(config: Config): PipeLineFilter = {
		val headerFilters = config.getOptionalConfig("header").getOrElse(ConfigFactory.empty).root.flatMap {
			case (h, hpattern: ConfigValue) => Some((h, hpattern.unwrapped.toString))
			case _ => None
		}.toMap

		PipeLineFilter(headerFilters,
									 config.getOptionalPattern("entity"),
									 config.getOptionalPattern("uri"),
									 config.getOptionalPattern("status"),
									 config.getOptionalPattern("method"))
	}
}

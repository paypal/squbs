package org.squbs.proxy

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
import org.squbs.unicomplex.ConfigUtil._

import scala.collection.JavaConversions._
import scala.collection.mutable.{HashMap => HMap}

/**
 * Created by jiamzhang on 15/2/14.
 */
/*case class PipeLineFilter(headers: Map[String, String] = Map.empty, entity: Option[Regex] = None, uri: Option[Regex] = None, status: Option[Regex] = None, method: Option[Regex] = None) {
	def fitHeader(hs: List[HttpHeader]): Boolean = {
		headers.forall { h =>
			hs.find(_.name == h._1) match {
				case Some(hh) => hh.value == h._2
				case _ => false
			}
		}
	}

	def fitEntity(e: HttpEntity): Boolean = {
		entity match {
			case Some(ent) => ent.pattern.matcher(e.asString).matches
			case _ => true
		}
	}

	def fitUri(u: Uri): Boolean = {
		uri match {
			case Some(ur) => ur.pattern.matcher(u.toRelative.toString()).matches
			case _ => true
		}
	}

	def fitMethod(m: HttpMethod): Boolean = {
		method match {
			case Some(mat) => mat.pattern.matcher(m.name.toLowerCase).matches
			case _ => true
		}
	}

	def fitStatus(s: StatusCode): Boolean = {
		status match {
			case Some(st) => st.pattern.matcher(s.intValue.toString()).matches
			case _ => true
		}
	}
}
case object PipeLineFilter {
	def empty = PipeLineFilter()
}*/

case class PipeLineConfig(handlers: Seq[_ <: Handler] = Seq.empty)/*, filter: PipeLineFilter) {
	def fitRequest(ctx: RequestContext): Boolean = {
		if (filter == PipeLineFilter.empty) true
		else {
			if (filter.fitHeader(ctx.request.headers) &&
					filter.fitUri(ctx.request.uri) &&
					filter.fitMethod(ctx.request.method) &&
					filter.fitEntity(ctx.request.entity))
				true
			else false
		}
	}

	def fitResponse(ctx: RequestContext): Boolean = {
		if (filter == PipeLineFilter.empty) true
		else {
			val resp = ctx.response match {
				case NormalResponse(resp) => resp
				case e: ExceptionalResponse => e.response
			}

			if (filter.fitHeader(resp.headers) &&
					filter.fitStatus(resp.status) &&
					filter.fitEntity(resp.entity.asString))
				true
			else false
		}
	}
}*/
object PipeLineConfig {
	val empty = PipeLineConfig()
}

case class PipeConfigInfo(reqConf: PipeLineConfig, respConf: PipeLineConfig)

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

			val reqPipe = conf.getOptionalStringList("inbound").getOrElse(Seq.empty)
			val respPipe = conf.getOptionalStringList("outbound").getOrElse(Seq.empty)

			val reqPipeObj = reqPipe.flatMap { h =>
				handlerCache.get(h)
			}

			val respPipeObj = respPipe.flatMap { h =>
				handlerCache.get(h)
			}

			responder ! PipeConfigInfo(PipeLineConfig(reqPipeObj), PipeLineConfig(respPipeObj))
	}

	/*private def buildFilter(config: Config): PipeLineFilter = {
		val headerFilters = config.getOptionalConfig("header").getOrElse(ConfigFactory.empty).root.flatMap {
			case (h, hpattern: ConfigValue) => Some((h, hpattern.unwrapped.toString))
			case _ => None
		}.toMap

		PipeLineFilter(headerFilters,
									 config.getOptionalPattern("entity"),
									 config.getOptionalPattern("uri"),
									 config.getOptionalPattern("status"),
									 config.getOptionalPattern("method"))
	}*/
}

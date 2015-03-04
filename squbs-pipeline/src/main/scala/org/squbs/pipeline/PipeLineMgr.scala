package org.squbs.pipeline

import akka.actor._
import com.typesafe.config.Config
import scala.collection.mutable.{HashMap => HMap}
import scala.util.Try

/**
 * Created by jiamzhang on 2015/3/3.
 */
class PipeLineMgr extends Extension {
	private val processorMap = new HMap[String, Processor]()

	def registerProcessor(name: String, processorFactory: String, config: Option[Config])(implicit actorRefFactory: ActorRefFactory) {
		try {
			processorMap.getOrElseUpdate(name, {
				val factory = Class.forName(processorFactory, true, getClass.getClassLoader).newInstance().asInstanceOf[ProcessorFactory]
				factory.create(config)
			})
		} catch {
			case t: Throwable =>
				t.printStackTrace()
		}
	}

	def getPipeLine(processorName: String, target: ActorRef, client: ActorRef, alias: String)(implicit actorRefFactory: ActorRefFactory): ActorRef = {
		val processor = processorMap.get(processorName) match {
			case Some(proc) => proc
			case _ => throw new IllegalArgumentException("No definition found for processor name:" + processorName)
		}
		actorRefFactory.actorOf(Props(classOf[PipeLineProcessorActor], target, client, processor), alias)
	}
}

object PipeLineMgr extends ExtensionId[PipeLineMgr] with ExtensionIdProvider {
	override def lookup = PipeLineMgr

	override def createExtension(system: ExtendedActorSystem) = new PipeLineMgr

	implicit class RichConfig(config: Config) {
		def getOptionalConfig(path: String): Option[Config] = Try(config.getConfig(path)).toOption

		def getOptionalString(path: String): Option[String] = Try(config.getString(path)).toOption

		def getOptionalStringList(path: String): Option[Seq[String]] = {
			val oplist = Try(config.getStringList(path)).toOption
			import scala.collection.JavaConversions._
			oplist.map(_.toSeq)
		}
	}
}
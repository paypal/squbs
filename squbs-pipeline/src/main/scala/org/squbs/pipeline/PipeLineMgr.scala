package org.squbs.pipeline

import akka.actor._
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import scala.collection.mutable.{HashMap => HMap}
import scala.util.Try

/**
 * Created by jiamzhang on 2015/3/3.
 */
class PipeLineMgr extends Extension {
	private val processorMap = new HMap[String, Processor]()
	private val logger = LoggerFactory.getLogger(getClass)

	def registerProcessor(name: String, processorFactory: String, config: Option[Config])(implicit actorRefFactory: ActorRefFactory) {
		try {
			processorMap.getOrElseUpdate(name, {
				val factory = Class.forName(processorFactory, true, getClass.getClassLoader).newInstance().asInstanceOf[ProcessorFactory]
				factory.create(config)
			})
		} catch {
			case t: Throwable =>
				logger.error(s"Can't instantiate the processor with name of $name and factory class name of $processorFactory.", t)
				throw t
		}
	}

	def registerProcessor(name: String, processor: Processor) {
		processorMap.getOrElseUpdate(name, processor)
	}

	def getPipeLine(processorName: String, target: ActorRef, client: ActorRef)(implicit actorRefFactory: ActorRefFactory): ActorRef = {
		val processor = processorMap.get(processorName) match {
			case Some(proc) => proc
			case _ => throw new IllegalArgumentException("No definition found for processor name:" + processorName)
		}
		getPipeLine(processor, target, client)
	}

	def getPipeLine(processor: Processor, target: ActorRef, client: ActorRef)(implicit actorRefFactory: ActorRefFactory): ActorRef = {
		actorRefFactory.actorOf(Props(classOf[PipeLineProcessorActor], target, client, processor))
	}
}

object PipeLineMgr extends ExtensionId[PipeLineMgr] with ExtensionIdProvider {
	override def lookup = PipeLineMgr

	override def createExtension(system: ExtendedActorSystem) = new PipeLineMgr
}
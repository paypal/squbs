package org.squbs.pipeline

import akka.actor._
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import scala.util.Try

/**
 * Created by jiamzhang on 2015/3/3.
 */
class PipelineMgr extends Extension {
  @volatile private var processorMap = Map.empty[String, Processor]
  private val logger = LoggerFactory.getLogger(getClass)

  def registerProcessor(name: String, processorFactory: String, config: Option[Config])(implicit actorRefFactory: ActorRefFactory) {
    synchronized {
      try {
        processorMap = processorMap + (name -> {
          val factory = Class.forName(processorFactory).newInstance().asInstanceOf[ProcessorFactory]
          factory.create(config)
        })
      } catch {
        case t: Throwable =>
          logger.error(s"Can't instantiate the processor with name of $name and factory class name of $processorFactory.", t)
          throw t
      }
    }
  }

  def registerProcessor(name: String, processor: Processor) {
		processorMap = processorMap + (name -> processor)
  }

  def getPipeline(processorName: String, target: ActorRef, client: ActorRef)(implicit actorRefFactory: ActorRefFactory): ActorRef = {
    val processor = processorMap.get(processorName) match {
      case Some(proc) => proc
      case _ => throw new IllegalArgumentException("No definition found for processor name:" + processorName)
    }
    actorRefFactory.actorOf(Props(classOf[PipelineProcessorActor], target, client, processor))
  }

  def getPipeline(processor: Processor, target: ActorRef, client: ActorRef)(implicit actorRefFactory: ActorRefFactory): ActorRef = {
    actorRefFactory.actorOf(Props(classOf[PipelineProcessorActor], target, client, processor))
  }

}

object PipelineMgr extends ExtensionId[PipelineMgr] with ExtensionIdProvider {
  override def lookup = PipelineMgr

  override def createExtension(system: ExtendedActorSystem) = new PipelineMgr

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
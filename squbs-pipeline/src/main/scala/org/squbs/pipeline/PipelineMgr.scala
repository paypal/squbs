package org.squbs.pipeline

import akka.actor._
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.util.Try

/**
 * Created by jiamzhang on 2015/3/3.
 */
class PipelineMgr extends Extension {
  //TODO use agent
  @volatile private var processorMap = Map.empty[String, Processor]
  private val logger = LoggerFactory.getLogger(getClass)

  def registerProcessor(name: String, processorFactory: String, config: Option[Config])(implicit actorRefFactory: ActorRefFactory): Option[Processor] = {
    synchronized {
      try {
        processorMap.get(name).fold {
          Class.forName(processorFactory).newInstance().asInstanceOf[ProcessorFactory].create(config) match {
            case None => None
            case v@Some(proc) =>
              processorMap = processorMap + (name -> proc)
              v
          }
        }(Some(_))
      } catch {
        case t: Throwable =>
          logger.error(s"Can't instantiate the processor with name of $name and factory class name of $processorFactory.", t)
          throw t
      }
    }

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
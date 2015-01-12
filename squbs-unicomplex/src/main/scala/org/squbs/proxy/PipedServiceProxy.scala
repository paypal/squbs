package org.squbs.proxy

import com.typesafe.config.Config
import akka.actor.Props
import scala.concurrent.{Promise, Future}

/**
 * Created by lma on 15-1-12.
 */

trait PipeHandler {

  def process(reqCtx: RequestContext): Future[RequestContext]

}

case class PipeLineConfig(
                           inbound: Seq[PipeHandler] = Seq.empty,
                           outbound: Seq[PipeHandler] = Seq.empty
                           )

abstract class PipedServiceProxy(settings: Option[Config], hostActorProps: Props) extends SimpleServiceProxy(settings, hostActorProps) {
  //inbound processing

  import context.dispatcher

  val pipeConfig = createPipeConfig

  //override to have your own logic, probably use settings
  //TODO generate pipeline from config
  def createPipeConfig(): PipeLineConfig

  def processRequest(reqCtx: RequestContext): Future[RequestContext] = {
    pipeConfig.inbound.foldLeft(Promise.successful(reqCtx).future)((ctx, handler) => ctx.flatMap(handler.process(_)))
  }

  //outbound processing
  def processResponse(reqCtx: RequestContext): Future[RequestContext] = {
    pipeConfig.outbound.foldLeft(Promise.successful(reqCtx).future)((ctx, handler) => ctx.flatMap(handler.process(_)))
  }
}

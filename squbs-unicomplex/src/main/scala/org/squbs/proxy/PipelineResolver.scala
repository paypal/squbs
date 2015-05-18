package org.squbs.proxy

import org.squbs.pipeline.Processor

/**
 * Created by lma on 2015/5/4.
 */
trait PipelineResolver {

  def resolve(config: SimplePipelineConfig): Option[Processor]
}

object SimplePipelineResolver extends PipelineResolver {
  override def resolve(config: SimplePipelineConfig): Option[Processor] = {
    if (SimplePipelineConfig.empty.equals(config)) None
    else Some(SimpleProcessor(config))
  }
}

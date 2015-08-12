/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.squbs.proxy

import com.typesafe.config.Config
import org.squbs.pipeline.Processor

trait PipelineResolver {

  def resolve(config: SimplePipelineConfig, setting: Option[Config]): Option[Processor]
}

object SimplePipelineResolver {
  val INSTANCE = new SimplePipelineResolver
}

class SimplePipelineResolver extends PipelineResolver {
  override def resolve(config: SimplePipelineConfig, setting: Option[Config]): Option[Processor] = {
    if (SimplePipelineConfig.empty.equals(config)) None
    else Some(SimpleProcessor(config))
  }

  def resolve(config: SimplePipelineConfig): Option[Processor] = {
    resolve(config, None)
  }
}

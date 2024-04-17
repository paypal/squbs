/*
 * Copyright 2019 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.squbs.unicomplex.timeout

import org.apache.pekko.NotUsed
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.stream.scaladsl.Flow
import org.slf4j.LoggerFactory
import org.squbs.unicomplex.FlowDefinition

object ConfigurableInitTimeActor {
  def log = LoggerFactory.getLogger(classOf[ConfigurableInitTimeActor])
}

class ConfigurableInitTimeActor extends FlowDefinition {
  import ConfigurableInitTimeActor.log

  override def flow: Flow[HttpRequest, HttpResponse, NotUsed] = {
    val system = this.context.system

    val initTime = Option(system.settings.config.getDuration("squbs.test.actor-init-time"))
      .get

    log.info(s"I'll be ready to go in $initTime")
    Thread.sleep(initTime.toMillis)
    log.info("Ready to work!")

    Flow[HttpRequest].map { r => HttpResponse(StatusCodes.OK, entity = "Hello") }
  }
}




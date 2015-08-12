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

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.agent.Agent

class PipelineResolverRegistry(entries: Agent[Map[String, PipelineResolver]]) extends Extension {

  var default: PipelineResolver = SimplePipelineResolver.INSTANCE

  def registerDefault(resolver: PipelineResolver) = default = resolver

  def register(name: String, resolver: PipelineResolver) = {
    entries.send {
      old => old + (name -> resolver)
    }
  }

  def getResolver(name: String): Option[PipelineResolver] = entries().get(name)

}

object PipelineResolverRegistry extends ExtensionId[PipelineResolverRegistry] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): PipelineResolverRegistry = {
    import system.dispatcher
    new PipelineResolverRegistry(Agent[Map[String, PipelineResolver]](Map.empty))
  }

  override def lookup(): ExtensionId[_ <: Extension] = PipelineResolverRegistry
}
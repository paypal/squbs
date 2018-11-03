package org.squbs.proxy

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.agent.Agent

/**
 * Created by lma on 2015/5/4.
 */
class PipelineResolverRegistry(entries: Agent[Map[String, PipelineResolver]]) extends Extension {

  var default: PipelineResolver = SimplePipelineResolver

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
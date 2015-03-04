package org.squbs.proxy

import akka.actor.{Extension, ExtendedActorSystem, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject}
import org.slf4j.{Logger, LoggerFactory}
import org.squbs.unicomplex.ConfigUtil._
import scala.collection.mutable
import scala.collection.JavaConversions._

/**
 * Created by jiamzhang on 2015/3/4.
 */
case class ProxySettings(default: Option[ProxySetup],
												 proxies: Map[String, ProxySetup]) extends Extension {

	def find(name: String): Option[ProxySetup] = {
		if(name.equals("default")) default
		else proxies.get(name)
	}
}

object ProxySettings extends ExtensionId[ProxySettings] with ExtensionIdProvider {

	override def lookup = ProxySettings

	override def createExtension(system: ExtendedActorSystem) = ProxySettings(system.settings.config)

	protected val logger: Logger = LoggerFactory.getLogger(getClass.getName)

	def apply(config: Config): ProxySettings = {
		val proxyConf = config.getOptionalConfig("squbs.proxy") getOrElse ConfigFactory.empty
		val proxyMap = mutable.Map.empty[String, ProxySetup]
		var default: Option[ProxySetup] = None
		proxyConf.root().foreach {
			case (key, cv: ConfigObject) =>
				try {
					val subCfg = cv.toConfig
					val aliasNames = subCfg.getOptionalStringList("aliases") getOrElse Seq.empty[String]
					val processorClassName = subCfg.getString("processor")
					val settings = subCfg.getOptionalConfig("settings")
					val proxySetup = ProxySetup(key, processorClassName, settings)

					aliasNames :+ key foreach { name =>
						if (proxyMap.contains(name))
							logger.warn("Alias name is already used by proxy: " + proxyMap.get(name).get.name)
						proxyMap.getOrElseUpdate(name, proxySetup)
					}
					if (key.equals("default")) {
						default = Some(proxySetup)
					}
				} catch {
					case t: Throwable =>
						logger.error("Error in parsing proxy setting", t)
				}
			case _ => // ignore any other setting inside the namespace
		}

		ProxySettings(default, proxyMap.toMap)
	}
}

case class ProxySetup(name: String,
											factoryClazz: String,
											settings: Option[Config])
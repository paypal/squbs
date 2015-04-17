/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.proxy

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject}
import org.slf4j.{Logger, LoggerFactory}
import org.squbs.unicomplex.ConfigUtil._

import scala.collection.JavaConversions._
import scala.collection.mutable

case class ProxySettings(default: Option[ProxySetup],
                         proxies: Map[String, ProxySetup]) extends Extension {

  def find(name: String): Option[ProxySetup] = {
    if (name.equals("default-proxy")) default
    else proxies.get(name)
  }
}

object ProxySettings extends ExtensionId[ProxySettings] with ExtensionIdProvider {

  override def lookup = ProxySettings

  override def createExtension(system: ExtendedActorSystem) = ProxySettings(system.settings.config)

  protected val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  def apply(config: Config): ProxySettings = {
    val proxyConf = config.getOptionalConfig("squbs.proxy") getOrElse ConfigFactory.empty

    //    val configs = proxyConf.root().flatMap{
    //      case (key, cv: ConfigObject) => Some((key, cv.toConfig))
    //      case other => None
    //    }.toSeq //

    val legacyConfigs = proxyConf.root().toSeq collect {
      case (key, cv: ConfigObject) => (key, cv.toConfig)
    }

    val proxyConfigs = config.root.toSeq collect {
      case (n, v: ConfigObject) if v.toConfig.getOptionalString("type") == Some("squbs.proxy") => (n, v.toConfig)
    }

    val proxyMap = genProxySetups(proxyConfigs ++ legacyConfigs)
    val default = proxyMap.get("default-proxy")
    ProxySettings(default, proxyMap)
  }

  def genProxySetups(configs: Seq[(String, Config)]): Map[String, ProxySetup] = {
    val proxyMap = mutable.Map.empty[String, ProxySetup]
    configs.foreach {
      conf =>
        try {
          val subCfg = conf._2
          val key = conf._1
          val aliasNames = subCfg.getOptionalStringList("aliases") getOrElse Seq.empty[String]
          val processorClassName = subCfg.getString("processorFactory") //throw error if not specified
          val settings = subCfg.getOptionalConfig("settings")
          val proxySetup = ProxySetup(key, processorClassName, settings)

          aliasNames :+ key foreach { name =>
            proxyMap.get(name) match {
              case None => proxyMap.put(name, proxySetup)
              case Some(value) => throw new IllegalArgumentException("Proxy name is already used by proxy: " + value.name)
            }
          }
        } catch {
          case t: Throwable =>
            logger.error("Error in parsing proxy setting", t)
            throw t
        }
    }

    proxyMap.toMap
  }
}

case class ProxySetup(name: String,
                      factoryClazz: String,
                      settings: Option[Config])
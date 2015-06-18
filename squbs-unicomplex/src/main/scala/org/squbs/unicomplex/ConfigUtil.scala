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
package org.squbs.unicomplex

import java.net.{Inet4Address, NetworkInterface}

import com.typesafe.config.{Config, ConfigException}

import scala.collection.JavaConversions._
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.matching.Regex

object ConfigUtil {

  implicit class RichConfig(val underlying: Config) extends AnyVal {

    def getOptionalString(path: String): Option[String] = {
      try {
        Option(underlying.getString(path))
      } catch {
        case e: ConfigException.Missing => None
      }
    }

    def getOptionalStringList(path: String): Option[Seq[String]] = {
      val list =
        try {
          Some(underlying.getStringList(path))
        } catch {
          case e: ConfigException.Missing => None
        }
      list map (_.toSeq)
    }


    def getOptionalInt(path: String): Option[Int] = {
      try {
        Option(underlying.getInt(path))
      } catch {
        case e: ConfigException.Missing => None
      }
    }

    def getOptionalBoolean(path: String): Option[Boolean] = {
      try {
        Option(underlying.getBoolean(path))
      } catch {
        case e: ConfigException.Missing => None
      }
    }

    def getOptionalConfig(path: String): Option[Config] = {
      try {
        Some(underlying.getConfig(path))
      } catch {
        case e: ConfigException.Missing => None
      }
    }


    def getOptionalConfigList(path: String): Option[Seq[Config]] = {
      val list =
        try {
          Some(underlying.getConfigList(path))
        } catch {
          case e: ConfigException.Missing => None
        }
      list map (_.toSeq)
    }

    def getOptionalDuration(path: String): Option[FiniteDuration] = {
      import scala.concurrent.duration._
      Try(Duration.create(underlying.getDuration(path, MILLISECONDS), MILLISECONDS)).toOption
    }

		def getOptionalPattern(path: String): Option[Regex] = {
			Try(new Regex(underlying.getString(path))).toOption
		}
  }

  def ipv4 = {
    val addresses = NetworkInterface.getNetworkInterfaces flatMap (_.getInetAddresses) filter { a =>
      a.isInstanceOf[Inet4Address] && !a.isLoopbackAddress
    }
    addresses.next().getHostAddress
  }

}

/*
 *  Copyright 2017 PayPal
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
package org.squbs.util

import java.net.{Inet4Address, NetworkInterface}

import com.typesafe.config.ConfigException.{Missing, WrongType}
import com.typesafe.config.{Config, ConfigException, ConfigMemorySize}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex
import scala.reflect.runtime.universe._

object ConfigUtil extends LazyLogging {

  private val StringTag = typeTag[String]
  private val StringListTag = typeTag[Seq[String]]
  private val ConfigTag = typeTag[Config]
  private val ConfigListTag = typeTag[Seq[Config]]
  private val RegexTag = typeTag[Regex]
  private val ConfigMemorySizeTag = typeTag[ConfigMemorySize]
  private val DurationTag = typeTag[Duration]
  private val FiniteDurationTag = typeTag[FiniteDuration]

  implicit class RichConfig(val underlying: Config) extends AnyVal {

    def getTry[T: TypeTag](path: String): Try[T] = Try {
      (typeTag[T] match {
        case StringTag => underlying.getString(path)
        case StringListTag => underlying.getStringList(path).asScala.toSeq
        case TypeTag.Int => underlying.getInt(path)
        case TypeTag.Boolean => underlying.getBoolean(path)
        case TypeTag.Double => underlying.getDouble(path)
        case ConfigTag => underlying.getConfig(path)
        case ConfigListTag => underlying.getConfigList(path).asScala.toSeq
        case RegexTag => new Regex(underlying.getString(path))
        case ConfigMemorySizeTag => underlying.getMemorySize(path)
        case FiniteDurationTag => Duration(underlying.getString(path)).asInstanceOf[FiniteDuration]
        case DurationTag => Duration(underlying.getString(path))
        case _ =>
          throw new IllegalArgumentException(s"Configuration option type ${typeTag[T].tpe} not implemented")
      }).asInstanceOf[T]
    } recover {
      case e: IllegalArgumentException => throw e
      case e: Missing => throw e
      case e: WrongType => throw e
      case e => throw new WrongType(underlying.origin,
        s"Path: $path, value ${underlying.getString(path)} is not a ${typeTag[T].tpe}", e)
    }

    def getOption[T: TypeTag](path: String): Option[T] =
      getTry[T](path) match {
        case Success(value) => Some(value)
        case Failure(e: ConfigException.Missing) => None
        case Failure(e: IllegalArgumentException) => throw e
        case Failure(e) =>
          logger.warn("Value at path {} has an illegal format for type{}: {}",
            path,  typeTag[T].tpe, underlying.getString(path))
          None
      }

    def get[T: TypeTag](path: String, default: => T) = getOption[T](path).getOrElse(default)

    def get[T: TypeTag](path: String) = getTry[T](path).get

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
      list map (_.asScala.toSeq)
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


    def getOptionalConfigList(path: String): Option[Seq[Config]] = try {
          Some(underlying.getConfigList(path).asScala.toSeq)
        } catch {
          case e: ConfigException.Missing => None
        }


    def getOptionalDuration(path: String): Option[FiniteDuration] = {
      import scala.concurrent.duration._
      Try(Duration.create(underlying.getDuration(path, MILLISECONDS), MILLISECONDS)).toOption
    }

    def getOptionalPattern(path: String): Option[Regex] = {
      Try(new Regex(underlying.getString(path))).toOption
    }

    def getOptionalMemorySize(path: String): Option[ConfigMemorySize] = {
      try {
        Some(underlying.getMemorySize(path))
      } catch {
        case e: ConfigException.Missing => None
      }
    }
  }

  def ipv4 = {
    val addresses = NetworkInterface.getNetworkInterfaces.asScala.flatMap (_.getInetAddresses.asScala) filter { a =>
      a.isInstanceOf[Inet4Address] && !a.isLoopbackAddress
    }
    addresses.next().getHostAddress
  }
}

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

import com.typesafe.config.ConfigException.{Missing, WrongType}
import com.typesafe.config._
import com.typesafe.scalalogging.LazyLogging
import org.squbs.util.DurationConverters._

import java.net.{Inet4Address, NetworkInterface}
import scala.annotation.implicitNotFound
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object ConfigUtil extends LazyLogging {

  @implicitNotFound("Cannot find `TypedGetter[T]` for accessing requested type from config")
  trait TypedGetter[T] {
    def get(config: Config, path:String): T
  }

  implicit val stringGetter: TypedGetter[String] = (config: Config, path: String) => config.getString(path)
  implicit val stringListGetter: TypedGetter[Seq[String]] =
    (config: Config, path: String) => config.getStringList(path).asScala.toSeq
  implicit val intGetter: TypedGetter[Int] = (config: Config, path: String) => config.getInt(path)
  implicit val intListGetter: TypedGetter[Seq[Int]] = (config: Config, path: String) =>
    config.getIntList(path).asScala.toSeq.asInstanceOf[Seq[Int]]
  implicit val longGetter: TypedGetter[Long] = (config: Config, path: String) => config.getLong(path)
  implicit val longListGetter: TypedGetter[Seq[Long]] = (config: Config, path: String) =>
    config.getLongList(path).asScala.toSeq.asInstanceOf[Seq[Long]]
  implicit val boolGetter: TypedGetter[Boolean] = (config: Config, path: String) => config.getBoolean(path)
  implicit val boolListGetter: TypedGetter[Seq[Boolean]] =
    (config: Config, path: String) => config.getBooleanList(path).asScala.toSeq.asInstanceOf[Seq[Boolean]]
  implicit val doubleGetter: TypedGetter[Double] = (config: Config, path: String) => config.getDouble(path)
  implicit val doubleListGetter: TypedGetter[Seq[Double]] =
    (config: Config, path: String) => config.getDoubleList(path).asScala.toSeq.asInstanceOf[Seq[Double]]
  implicit val configGetter: TypedGetter[Config] = (config: Config, path: String) => config.getConfig(path)
  implicit val configListGetter: TypedGetter[Seq[Config]] =
    (config: Config, path: String) => config.getConfigList(path).asScala.toSeq
  implicit val objectGetter: TypedGetter[ConfigObject] = (config: Config, path: String) => config.getObject(path)
  implicit val objectListGetter: TypedGetter[Seq[ConfigObject]] =
    (config: Config, path: String) => config.getObjectList(path).asScala.toSeq
  implicit val regexGetter: TypedGetter[Regex] = (config: Config, path: String) => new Regex(config.getString(path))
  implicit val regexListGetter: TypedGetter[Seq[Regex]] =
    (config: Config, path: String) => config.getStringList(path).asScala.toSeq.map(new Regex(_))
  implicit val memSizeGetter: TypedGetter[ConfigMemorySize] =
    (config: Config, path: String) => config.getMemorySize(path)
  implicit val memSizeListGetter: TypedGetter[Seq[ConfigMemorySize]] =
    (config: Config, path: String) => config.getMemorySizeList(path).asScala.toSeq
  implicit val fdGetter: TypedGetter[FiniteDuration] =
    (config: Config, path: String) => config.getDuration(path).toScala
  implicit val fdListGetter: TypedGetter[Seq[FiniteDuration]] =
    (config: Config, path: String) => config.getDurationList(path).asScala.toSeq.map(_.toScala)
  implicit val durGetter: TypedGetter[Duration] = (config: Config, path: String) =>
    try {
      config.getDuration(path).toScala
    } catch {
      case _: ConfigException.BadValue if config.getString(path) == "Inf" => Duration.Inf
    }
  implicit val durListGetter: TypedGetter[Seq[Duration]] = (config: Config, path: String) =>
    try {
      config.getDurationList(path).asScala.toSeq.map(_.toScala)
    } catch { // Since 'Inf' is not implicitly supported, dealing with it in a list is a little messy.
      case _: ConfigException.BadValue =>
        config.getStringList(path).asScala.toSeq.map { elem =>
          val tmpConfig = ConfigFactory.parseString(s"some-key = $elem")
          try
            tmpConfig.getDuration("some-key").toScala
          catch {
            case _: ConfigException.BadValue if tmpConfig.getString("some-key") == "Inf" => Duration.Inf
          }
        }
    }
  implicit val anyRefGetter: TypedGetter[AnyRef] = (config: Config, path: String) => config.getAnyRef(path)
  implicit val anyRefListGetter: TypedGetter[Seq[_]] =
    (config: Config, path: String) => config.getAnyRefList(path).asScala.toSeq

  implicit class RichConfig(val underlying: Config) extends AnyVal {

    def getTry[T: TypedGetter](path: String): Try[T] = Try {
      implicitly[TypedGetter[T]].get(underlying, path)
    } recover {
      case e: IllegalArgumentException => throw e
      case e: Missing => throw e
      case e: WrongType => throw e
      case e => throw new WrongType(underlying.origin,
        s"Path: $path, value ${underlying.getString(path)} is not the correct type", e)
    }

    def getOption[T: TypedGetter](path: String): Option[T] =
      getTry[T](path) match {
        case Success(value) => Some(value)
        case Failure(_: ConfigException.Missing) => None
        case Failure(e: IllegalArgumentException) => throw e
        case Failure(_) =>
          logger.warn("Value at path {} has an illegal format for type: {}",
            path, underlying.getString(path))
          None
      }

    def get[T: TypedGetter](path: String, default: => T): T = getOption[T](path).getOrElse(default)

    def get[T: TypedGetter](path: String): T = getTry[T](path).get

    def getOptionalString(path: String): Option[String] = {
      try {
        Option(underlying.getString(path))
      } catch {
        case _: ConfigException.Missing => None
      }
    }

    def getOptionalStringList(path: String): Option[Seq[String]] = {
      val list =
        try {
          Some(underlying.getStringList(path))
        } catch {
          case _: ConfigException.Missing => None
        }
      list map (_.asScala.toSeq)
    }


    def getOptionalInt(path: String): Option[Int] = {
      try {
        Option(underlying.getInt(path))
      } catch {
        case _: ConfigException.Missing => None
      }
    }

    def getOptionalBoolean(path: String): Option[Boolean] = {
      try {
        Option(underlying.getBoolean(path))
      } catch {
        case _: ConfigException.Missing => None
      }
    }

    def getOptionalConfig(path: String): Option[Config] = {
      try {
        Some(underlying.getConfig(path))
      } catch {
        case _: ConfigException.Missing => None
      }
    }


    def getOptionalConfigList(path: String): Option[Seq[Config]] = try {
          Some(underlying.getConfigList(path).asScala.toSeq)
        } catch {
          case _: ConfigException.Missing => None
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
        case _: ConfigException.Missing => None
      }
    }
  }

  def ipv4: String = {
    val addresses = NetworkInterface.getNetworkInterfaces.asScala.flatMap (_.getInetAddresses.asScala) filter { a =>
      a.isInstanceOf[Inet4Address] && !a.isLoopbackAddress
    }
    addresses.next().getHostAddress
  }
}

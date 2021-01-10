/*
 * Copyright 2017 PayPal
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

package org.squbs.admin

import java.lang.management.ManagementFactory
import javax.management.{InstanceNotFoundException, ObjectName}
import javax.management.openmbean.{CompositeData, TabularData}

import com.fasterxml.jackson.core.util.{DefaultPrettyPrinter, DefaultIndenter}
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import scala.jdk.CollectionConverters._
import scala.util.Try

object MBeanUtil {

  val server = ManagementFactory.getPlatformMBeanServer

  def allObjectNames: List[String] = {
    val beans = server.queryMBeans(null, null)
    beans.asScala.map { bean =>
      bean.getObjectName.toString
    } .toList.sorted
  }

  def asJSON(beanName: String, exclusions: Set[String] = Set.empty): Option[String] = {
    val objectName = new ObjectName(beanName)
    val c = new MBean2JSON(exclusions)
    try {
      val fields = server.getMBeanInfo(objectName).getAttributes.toList.map { _.getName}.sorted.collect {
        case name if !(exclusions contains name) => name -> c.eval {
          server.getAttribute(objectName, name)
        }
      }
      Option(prettyPrint(render(fields)))
    } catch {
      case e: InstanceNotFoundException => None
    }
  }

  private def prettyPrint(v: JValue) = {
    val printer = new DefaultPrettyPrinter().withArrayIndenter(new DefaultIndenter)
    mapper.writer(printer).writeValueAsString(v)
  }

  class MBean2JSON(exclusions: Set[String]) {

    private def toJValue(valueOption: Option[Any]): JValue = valueOption match {
      case Some(value) => value match {
        case table: TabularData =>
          val list = table.values.toArray
          optionJObject(list) getOrElse {
            toJArray(list)
          }
        case list: Array[_] => optionJObject(list) getOrElse {
          toJArray(list)
        }
        case c: CompositeData => toJObject(c)
        case v: java.lang.Double => v.doubleValue
        case v: java.lang.Float => v.floatValue
        case v: java.lang.Number => v.longValue
        case v: java.lang.Boolean => v.booleanValue
        case v => v.toString
      }
      case None => JNull
    }

    private def toJArray(list: Array[_]) = JArray(list.toList map { v => toJValue(Option(v)) })

    private def optionJObject(list: Array[_]) = {
      try {
        val kc = list.map {
          case c: CompositeData =>
            val keySet = c.getCompositeType.keySet
            if (keySet.size == 2 && keySet.contains("key") && keySet.contains("value"))
              c.get("key").asInstanceOf[String] -> c
            else throw new ClassCastException("CompositeData member is not a key/value pair")
          case _ => throw new ClassCastException("Non-CompositeData value")
        }
        val fields = kc.toList collect {
          case (k, c) if !(exclusions contains k) => k -> eval { c.get("value") }
        }
        Some(JObject(fields))
      } catch {
        case e: ClassCastException => None
      }
    }

    private def toJObject(c: CompositeData) =
      JObject(c.getCompositeType.keySet.asScala.toList.sorted collect {
        case key if !(exclusions contains key) => key -> eval { c.get(key)}
      })

    // Note: Try {...} .toOption can give you a Some(null), especially with Java APIs.
    // Need to flatMap with Option again to ensure Some(null) is None.
    def eval(fn: => Any) = toJValue(Try { fn } .toOption flatMap { v => Option(v) })
  }
}

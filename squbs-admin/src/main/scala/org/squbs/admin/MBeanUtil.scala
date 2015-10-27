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

package org.squbs.admin

import java.lang.management.ManagementFactory
import javax.management.ObjectName
import javax.management.openmbean.{CompositeData, TabularData}

import scala.collection.JavaConversions._
import scala.collection.immutable.StringOps

object MBeanUtil {

  val indentStep = 2

  val server = ManagementFactory.getPlatformMBeanServer

  def allObjectNames: Seq[String] = {
    val beans = server.queryMBeans(null, null)
    beans.map { bean =>
      bean.getObjectName.toString
    } .toSeq.sorted
  }

  def asJSON(beanName: String): String = {
    val objectName = new ObjectName(beanName)
    server.getMBeanInfo(objectName).getAttributes.map { _.getName } .sorted.map { name =>
      writeProperty(name, option { server.getAttribute(objectName, name) }, indentStep)
    } .mkString("{\n", ",\n", "\n}")
  }

  private def writeProperty(key: String, value: Option[AnyRef], indent: Int): String = {
    val pad = space(indent)
    value match {

      case Some(table: TabularData) =>
        val list = table.values.toArray
        val render = option { writeKeyValueList(list, indent) } getOrElse { writeArray(list, indent) }
        s"""$pad"$key" : $render"""

      case Some(list: Array[_]) =>
        val render = option { writeKeyValueList(list, indent) } getOrElse { writeArray(list, indent) }
        s"""$pad"$key" : $render"""

      case Some(c: CompositeData) => s"""$pad"$key" : ${writeProperties(c, indent)}"""

      case Some(v: java.lang.Number) => s"""$pad"$key" : $v"""

      case Some(v: java.lang.Boolean) => s"""$pad"$key" : $v"""

      case Some(v) => s"""$pad"$key" : "${clean(v.toString)}""""

      case None => s"""$pad"$key" : null"""
    }
  }

  private def writeArray(list: Array[_], indent: Int): String = {
    val pad = space(indent)
    val pad2 = space(indent + indentStep)
    list.map {
      case c: CompositeData => pad2 + writeProperties(c, indent + indentStep)
      case n: java.lang.Number => pad2 + n
      case b: java.lang.Boolean => pad2 + b
      case v => s"""$pad2"${clean(v.toString)}""""
    } .mkString("[\n", ",\n", s"\n$pad]")
  }

  private def writeKeyValueList(list: Array[_], indent: Int): String = {
    val pad = space(indent)
    val kv = list.map {
      case c: CompositeData =>
        val keySet = c.getCompositeType.keySet
        if (keySet.size == 2 && keySet.contains("key") && keySet.contains("value"))
          c.get("key").asInstanceOf[String] -> c
        else throw new ClassCastException("CompositeData member is not a key/value pair")
      case _ => throw new ClassCastException("Non-CompositeData value")
    }
    kv.map {
      case (k, c) => writeProperty(k, option { c.get("value") }, indent + indentStep)
    } .mkString("{\n", ",\n", s"\n$pad}")
  }

  private def writeProperties(c: CompositeData, indent: Int): String = {
    val pad = space(indent)
    c.getCompositeType.keySet.toSeq.sorted.map { key =>
      writeProperty(key, option { c.get(key) }, indent + indentStep)
    } .mkString("{\n", ",\n", s"\n$pad}")
  }

  private def space(indent: Int): String = new StringOps(" ") * indent

  private def clean(s: String): String = s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

  private def option[T](fn: => T): Option[T] = {
    try {
      Option(fn)
    } catch {
      case e: Exception => None
    }
  }
}

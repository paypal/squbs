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

import java.beans.ConstructorProperties
import java.lang.management.ManagementFactory
import javax.management.{MXBean, ObjectName}

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{Inspectors, BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.beans.{BeanProperty, BooleanBeanProperty}
import scala.collection.JavaConversions._

class MBeanUtilTest extends FunSpecLike with Matchers with BeforeAndAfterAll with Inspectors {

  override def beforeAll() {

    val testBean = TestBean("Hello", 123.456, Long.MaxValue, props03 = false,
      AnotherTestObject("Hi TestObject", props1 = true), Array(1.2, 1.5, 1.8, 2.1), Array(true, false, false, true),
      Array("foo", "bar", "foobar", "baz"), Array(AnotherTestObject("Hi TestObject1", props1 = true),
        AnotherTestObject("Hi TestObject2", props1 = false)), Array(KeyValueObject("foo", "bar"),
        KeyValueObject("foobar", "baz")),
      Map("foo" -> AnotherTestObject("Hi TestObject3", props1 = false),
        "bar" -> AnotherTestObject("Hi TestObject4", props1 = true)))

    ManagementFactory.getPlatformMBeanServer.registerMBean(testBean,
      new ObjectName("org.squbs.admin.test:type=TestBean"))
  }

  override def afterAll(): Unit = {
    ManagementFactory.getPlatformMBeanServer.unregisterMBean(new ObjectName("org.squbs.admin.test:type=TestBean"))
  }

  it ("should render TestMXBean with proper indentation.") {
    val expectedJSON =
      """{
        |  "Props00" : "Hello",
        |  "Props01" : 123.456,
        |  "Props02" : 9223372036854775807,
        |  "Props03" : false,
        |  "Props04" : {
        |    "props0" : "Hi TestObject",
        |    "props1" : true
        |  },
        |  "Props05" : [
        |    1.2,
        |    1.5,
        |    1.8,
        |    2.1
        |  ],
        |  "Props06" : [
        |    true,
        |    false,
        |    false,
        |    true
        |  ],
        |  "Props07" : [
        |    "foo",
        |    "bar",
        |    "foobar",
        |    "baz"
        |  ],
        |  "Props08" : [
        |    {
        |      "props0" : "Hi TestObject1",
        |      "props1" : true
        |    },
        |    {
        |      "props0" : "Hi TestObject2",
        |      "props1" : false
        |    }
        |  ],
        |  "Props09" : {
        |    "foo" : "bar",
        |    "foobar" : "baz"
        |  },
        |  "Props10" : {
        |    "foo" : {
        |      "props0" : "Hi TestObject3",
        |      "props1" : false
        |    },
        |    "bar" : {
        |      "props0" : "Hi TestObject4",
        |      "props1" : true
        |    }
        |  }
        |}""".stripMargin

    val testBeanJSON = MBeanUtil.asJSON("org.squbs.admin.test:type=TestBean")
    testBeanJSON shouldBe expectedJSON
  }

  it ("should list relevant JMX beans in the system") {
    MBeanUtil.allObjectNames should contain allOf (
      "org.squbs.admin.test:type=TestBean",
      "java.lang:type=Runtime",
      "java.lang:type=OperatingSystem")
  }

  it ("should provide valid JSON for all JMX beans in the system") {
    forAll (MBeanUtil.allObjectNames) { name =>
      noException should be thrownBy parse(MBeanUtil.asJSON(name))
    }
  }
}

@MXBean
trait TestMXBean {
  def getProps00: String
  def getProps01 : Double
  def getProps02: Long
  def isProps03: Boolean
  def getProps04: AnotherTestObject
  def getProps05: Array[Double]
  def getProps06: Array[Boolean]
  def getProps07: Array[String]
  def getProps08: Array[AnotherTestObject]
  def getProps09: Array[KeyValueObject]
  def getProps10: java.util.Map[String, AnotherTestObject]
}

case class TestBean(@BeanProperty props00: String, @BeanProperty props01: Double, @BeanProperty props02: Long,
                    @BooleanBeanProperty props03: Boolean, @BeanProperty props04: AnotherTestObject,
                    @BeanProperty props05: Array[Double], @BeanProperty props06: Array[Boolean],
                    @BeanProperty props07: Array[String], @BeanProperty props08: Array[AnotherTestObject],
                    @BeanProperty props09: Array[KeyValueObject],
                    @BeanProperty props10: java.util.Map[String, AnotherTestObject])
  extends TestMXBean

case class AnotherTestObject @ConstructorProperties(Array("props0", "props1"))(@BeanProperty props0: String,
                                                                               @BooleanBeanProperty props1: Boolean)

case class KeyValueObject @ConstructorProperties(Array("key", "value"))(@BeanProperty key: String,
                                                                        @BeanProperty value: String)



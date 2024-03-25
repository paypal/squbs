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

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.json4s.jackson.JsonMethods._
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.testkit.TestRoute

import java.lang.management.ManagementFactory
import javax.management.{MXBean, ObjectName}
import scala.beans.BeanProperty

class AdminSvcTest extends AnyFunSpecLike with Matchers with ScalatestRouteTest {

  ManagementFactory.getPlatformMBeanServer.registerMBean(SlashTestBean("foo"),
    new ObjectName("org.squbs.admin.test:type=SlashTestBean/FooBean"))


  describe ("The AdminSvc route with root web context") {

    val route = TestRoute[AdminSvc]

    it ("should provide the MBean list for root request") {
      Get("/") ~> route ~> check {
        val response = responseAs[String]
        noException should be thrownBy parse(response)
        response should include (""""java.lang:type=Runtime" : """)
        response should include (""""java.lang:type=OperatingSystem" :""")
        response should include (""""org.squbs.admin.test:type=SlashTestBean/FooBean" :""")
      }
    }

    it ("should provide proper JSON in response") {
      Get("/bean/java.lang:type~OperatingSystem") ~> route ~> check {
        noException should be thrownBy parse(responseAs[String])
      }
    }

    it ("should provide proper JSON in response to bean with slashes in name") {
      Get("/bean/org.squbs.admin.test:type~SlashTestBean%25FooBean") ~> route ~> check {
        noException should be thrownBy parse(responseAs[String])
      }
    }

    it ("should return status NotFound accessing an invalid bean name") {
      Get("/bean/java.lang:type~FooBar") ~> route ~> check {
        response.status shouldBe StatusCodes.NotFound
      }
    }

    it ("should encode the JMX bean name") {
      Get("/") ~> route ~> check {
        val name = "org.squbs.admin.test:type=SlashTestBean/FooBean"
        val value = "http://example.com/bean/org.squbs.admin.test:type~SlashTestBean%2FFooBean"
        responseAs[String] should include (s""""$name" : "$value"""")
      }
    }
  }

  describe ("The AdminSvc route with adm web context") {

    val route = TestRoute[AdminSvc](webContext = "adm")

    it ("should provide the MBean list for root request") {
      Get("/adm") ~> route ~> check {
        val response = responseAs[String]
        noException should be thrownBy parse(response)
        response should include (""""java.lang:type=Runtime" : """)
        response should include (""""java.lang:type=OperatingSystem" :""")
        response should include (""""org.squbs.admin.test:type=SlashTestBean/FooBean" :""")
      }
    }

    it ("should provide proper JSON in response") {
      Get("/adm/bean/java.lang:type~OperatingSystem") ~> route ~> check {
        noException should be thrownBy parse(responseAs[String])
      }
    }

    it ("should provide proper JSON in response to bean with slashes in name") {
      Get("/adm/bean/org.squbs.admin.test:type~SlashTestBean%25FooBean") ~> route ~> check {
        noException should be thrownBy parse(responseAs[String])
      }
    }

    it ("should return status NotFound accessing an invalid bean name") {
      Get("/adm/bean/java.lang:type~FooBar") ~> route ~> check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }
}

class AdminSvcWithExclusionTest extends AnyFunSpecLike with Matchers with ScalatestRouteTest {

  override def testConfigSource = """
      |squbs.admin {
      |  exclusions = [ "java.lang:type=Memory", "java.lang:type=GarbageCollector,name=PS MarkSweep::init" ]
      |}
    """.stripMargin


  describe ("The AdminSvc route with root web context") {

    val route = TestRoute[AdminSvc]

    it ("should provide the MBean list for root request") {
      Get("/") ~> route ~> check {
        val response = responseAs[String]
        noException should be thrownBy parse(response)
        response should include (""""java.lang:type=Runtime" : """)
        response should include (""""java.lang:type=OperatingSystem" :""")
        response should not include """"java.lang:type=Memory" :"""
      }
    }

    it ("should provide proper JSON with correct field exclusions in response") {
      Get("/bean/java.lang:type~GarbageCollector,name~PS%20MarkSweep") ~> route ~> check {
        val json = responseAs[String]
        noException should be thrownBy parse(json)
        json should not include """"init" :"""
      }
    }

    it ("should return status NotFound accessing an excluded bean") {
      Get("/bean/java.lang:type~Memory") ~> route ~> check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }

  describe ("The AdminSvc route with adm web context") {

    val route = TestRoute[AdminSvc](webContext = "adm")

    it ("should provide the MBean list for root request") {
      Get("/adm") ~> route ~> check {
        val response = responseAs[String]
        noException should be thrownBy parse(response)
        response should include (""""java.lang:type=Runtime" : """)
        response should include (""""java.lang:type=OperatingSystem" :""")
        response should not include """"java.lang:type=Memory" :"""
      }
    }

    it ("should provide proper JSON  with correct field exclusions in response") {
      Get("/adm/bean/java.lang:type~OperatingSystem") ~> route ~> check {
        val json = responseAs[String]
        noException should be thrownBy parse(json)
        json should not include """"init" :"""
      }
    }

    it ("should return status NotFound accessing an excluded bean") {
      Get("/adm/bean/java.lang:type~Memory") ~> route ~> check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }
}

@MXBean
trait SlashTestMxBean {
  def getProp: String
}

case class SlashTestBean(@BeanProperty prop: String) extends SlashTestMxBean

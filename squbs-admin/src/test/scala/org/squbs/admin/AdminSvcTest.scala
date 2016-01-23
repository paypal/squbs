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

import org.json4s.jackson.JsonMethods._
import org.scalatest.{FunSpecLike, Matchers}
import org.squbs.testkit.TestRoute
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest

class AdminSvcTest extends FunSpecLike with Matchers with ScalatestRouteTest {

  describe ("The AdminSvc route with root web context") {

    val route = TestRoute[AdminSvc]

    it ("should provide the MBean list for root request") {
      Get("/") ~> route ~> check {
        val response = responseAs[String]
        noException should be thrownBy parse(response)
        response should include (""""java.lang:type=Runtime" : """)
        response should include (""""java.lang:type=OperatingSystem" :""")
      }
    }

    it ("should provide proper JSON in response") {
      Get("/bean/java.lang:type~OperatingSystem") ~> route ~> check {
        noException should be thrownBy parse(responseAs[String])
      }
    }

    it ("should return status NotFound accessing an invalid bean name") {
      Get("/bean/java.lang:type~FooBar") ~> route ~> check {
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
      }
    }

    it ("should provide proper JSON in response") {
      Get("/adm/bean/java.lang:type~OperatingSystem") ~> route ~> check {
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

class AdminSvcWithExclusionTest extends FunSpecLike with Matchers with ScalatestRouteTest {

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

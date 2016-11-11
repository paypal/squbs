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

package org.squbs.pattern.validation

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.MethodDirectives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{FunSpecLike, Matchers}
import org.squbs.pattern.validation.ValidationDirectives.{validate => _}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object MyJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val PersonFormat: RootJsonFormat[Person] = jsonFormat4(Person)
}

class ValidationDirectivesSpec extends FunSpecLike with Matchers with ScalatestRouteTest{

  val ValidationPassed = "Validation Passed"

  import MyJsonProtocol._
  import org.squbs.pattern.validation.SampleValidators._

  val route: Route =
    (path("person") & MethodDirectives.post) {
      entity(as[Person]) { person =>
        import ValidationDirectives._
        validate(person) {
          complete(ValidationPassed)
        }
      }
    }

  describe("ValidationDirectives") {

    it(s"should return [$ValidationPassed] string with 200 for a valid content without middle name") {
      Post("/person", Person("John", "Smith", age = 25)) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual ValidationPassed
      }
    }

    it(s"should return [$ValidationPassed] string with 200 for a valid content with middle name") {
      Post("/person", Person("John", "Smith", Some("Mike"), age = 25)) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual ValidationPassed
      }
    }

    it("should reject with Last Name") {
      Post("/person", Person("John", "", age = 25)) ~> route ~> check {
        rejections shouldEqual List(ValidationRejection("Last Name"))
      }
    }

    it("should reject with middleName") {
      Post("/person", Person("John", "Smith", Some(""), age = 25)) ~> route ~> check {
        rejections shouldEqual List(ValidationRejection("middleName"))
      }
    }

    it("should reject with Last Name, middleName, age") {
      Post("/person", Person("John", "", Some(""), age = -1)) ~> route ~> check {
        rejections shouldEqual List(ValidationRejection("Last Name, middleName, age"))
      }
    }
  }

}

package org.squbs.pattern.validation

import org.scalatest.{FlatSpecLike, Matchers}
import org.squbs.pattern.validation.SampleValidators._
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.httpx.marshalling._
import spray.json.DefaultJsonProtocol
import spray.routing.Directives._
import spray.routing.ValidationRejection
import spray.routing.directives.MarshallingDirectives
import spray.testkit.ScalatestRouteTest

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val PersonFormat = DefaultJsonProtocol.jsonFormat4(Person)
}

class ValidationDirectivesTest extends FlatSpecLike with Matchers with ScalatestRouteTest {

  val validData1 = Person("John", "Smith", age = 25)
  val validData2 = Person("John", "Smith", Some("Mike"), age = 25)

  val invalidData1 = Person("John", "", age = 25)
  val invalidData2 = Person("John", "Smith", Some(""), age = 25)
  val invalidData3 = Person("John", "", Some(""), age = -1)

  import MyJsonProtocol._

  val route =
    path("person") {
      post {
        MarshallingDirectives.entity(as[Person]) { person =>
          import ValidationDirectives._
          validate(person) {
            respondWithMediaType(`application/json`) {
              complete {
                person
              }
            }
          }
        }
      }
    }


  it should "return the same object with 200 for a valid content without middle name" in {
    Post("/person", validData1) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      Right(body) shouldEqual marshal(validData1)
    }
  }

  it should "return the same object with 200 for a valid content with middle name" in {
    Post("/person", validData2) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      Right(body) shouldEqual marshal(validData2)
    }
  }

  it should "reject with Last Name" in {
    Post("/person", invalidData1) ~> route ~> check {
      rejections shouldEqual List(ValidationRejection("Last Name"))
    }
  }

  it should "reject with middleName" in {
    Post("/person", invalidData2) ~> route ~> check {
      rejections shouldEqual List(ValidationRejection("middleName"))
    }
  }

  it should "reject with Last Name, middleName, age" in {
    Post("/person", invalidData3) ~> route ~> check {
      rejections shouldEqual List(ValidationRejection("Last Name, middleName, age"))
    }
  }
}

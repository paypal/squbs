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

package org.squbs.httpclient.dummy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.json4s.DefaultFormats
import org.squbs.marshallers.json.TestData._
import org.squbs.marshallers.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class DummyServiceJavaTest extends DummyService

trait DummyService {

  val caseClassMapper = new ObjectMapper().registerModule(DefaultScalaModule)
  val fieldMapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
    .registerModule(DefaultScalaModule)

  XLangJsonSupport.register[TeamBeanWithCaseClassMember](caseClassMapper)
  XLangJsonSupport.register[TeamWithPrivateMembers](fieldMapper)


  def startService(implicit system: ActorSystem): Future[Int] = {
    import system.dispatcher

    val serverBindingF: Future[ServerBinding] = Http().newServerAt("0.0.0.0", 0).bind(route)

    serverBindingF onComplete {
      case Success(b) =>
        println(StringContext("Successfully bound to ", "").s(b.localAddress))
      case Failure(ex) =>
        println(ex.getMessage)
        system.terminate()
    }

    serverBindingF map { binding => binding.localAddress.getPort }
  }

  def route(implicit system: ActorSystem): Route = {

    import Directives._
    import XLangJsonSupport.typeToUnmarshaller

    import scala.reflect.runtime.universe._

    XLangJsonSupport.setDefaultFormats(DefaultFormats + EmployeeBeanSerializer)

    implicit def superMarshaller[T <: AnyRef: TypeTag]: ToEntityMarshaller[T] = Marshaller.oneOf[T, MessageEntity](
      XLangJsonSupport.typeToMarshaller[T]
      // Add other marshallers for content type negotiation here.
    )

    pathSingleSlash {
      redirect("/view", StatusCodes.Found)
    } ~
      //get, head, options
      path("view") {
        (get | head | options | post) {
          headerValueByName("req1-name") { value =>
            headerValueByName("req2-name") { value2 =>
              respondWithHeader(RawHeader("res-req1-name", "res-" + value)) {
                respondWithHeader(RawHeader("res-req2-name", "res2-" + value2)) {
                  complete {
                    fullTeam
                  }
                }
              }
            } ~
              respondWithHeader(RawHeader("res-req1-name", "res-" + value)) {
                complete {
                  fullTeam
                }
              }
          } ~
            headerValueByName("req2-name") {
              value =>
                respondWithHeader(RawHeader("res-req2-name", "res-" + value)) {
                  complete {
                    fullTeam
                  }
                }

            } ~
            complete {
              fullTeam
            }
        }
      } ~
      //get, head, options
      path("viewj") {
        (get | head | options | post) {
          complete {
            fullTeamWithPrivateMembers
          }
        }
      } ~
      path("view1") {
        (get | head | options | post) {
          complete {
            fullTeamNonCaseClass
          }
        }
      } ~
      path("view2") {
        (get | head | options | post) {
          complete {
            fullTeamWithBeanMember
          }
        }
      } ~
      path("view3") {
        (get | head | options | post) {
          complete {
            fullTeamWithCaseClassMember
          }
        }
      } ~
      path("paged") {
        get {
          complete {
            pageTest
          }
        }
      } ~
      path("viewrange") {
        (get | head | options) {
          parameters("range") { range =>
            complete {
              if (range == "new") newTeamMember else fullTeam
            }
          }
        }
      } ~
      path("stop") {
        post {
          complete {
            system.scheduler.scheduleOnce(1.second)(system.terminate())(system.dispatcher)
            "Shutting down in 1 second..."
          }
        } ~
        parameter("method" ! "post") { _ =>
          complete {
            system.scheduler.scheduleOnce(1.second)(system.terminate())(system.dispatcher)
            "Shutting down in 1 second..."
          }
        }
      } ~
      path("timeout") {
        (get | head | options) {
          complete {
            Thread.sleep(3000)
            "Thread 3 seconds, then return!"
          }
        }
      } ~
      //post, put
      path("add") {
        (post | put) {
          entity[Employee](as[Employee]) {
            employee: Employee =>
              complete {
                Team(fullTeam.description, fullTeam.members :+ employee)
              }
          }
        }
      } ~
      path("addj") {
        (post | put) {
          entity[EmployeeBean](as[EmployeeBean]) {
            employee: EmployeeBean =>
              complete {
                fullTeamWithPrivateMembers.addMember(employee)
              }
          }
        }
      } ~
      //del
      path("del" / LongNumber) {
        id =>
          delete {
            complete {
              val employee = fullTeam.members.find(_.id == id)
              employee match {
                case Some(emp) => Team(fullTeam.description, fullTeam.members.filterNot(_ == emp))
                case None => "cannot find the employee"
              }
            }
          }
      } ~
      path("emptyresponse") {
        complete {
          HttpResponse(status = StatusCodes.NoContent)
        }
      }
  }
}

/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient.dummy

import spray.routing.SimpleRoutingApp
import akka.actor.ActorSystem
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import spray.util.Utils._

case class Employee(id: Long, firstName: String, lastName: String, age: Int, male: Boolean)

case class Team(description: String, members: List[Employee])

object DummyService {

  val (dummyServiceIpAddress, dummyServicePort) = temporaryServerHostnameAndPort()

  val dummyServiceEndpoint = s"http://$dummyServiceIpAddress:$dummyServicePort"
}

object DummyServiceMain extends App with DummyService {
  implicit val actorSystem = ActorSystem("DummyServiceMain")
  startDummyService(actorSystem, address = "localhost", port = 8888)
}

trait DummyService extends SimpleRoutingApp {

  val fullTeam = Team("Scala Team", List[Employee](
    Employee(1, "Zhuchen", "Wang", 20, male = true),
    Employee(2, "Roy", "Zhou", 25, male = true),
    Employee(3, "Ping", "Zhao", 30, male = false),
    Employee(4, "Dennis", "Kuang", 35, male = false)
  ))

  val newTeamMember = Employee(5, "Leon", "Ma", 35, male = true)

  val fullTeamJson = "{\"description\":\"Scala Team\",\"members\":[{\"id\":1,\"firstName\":\"Zhuchen\",\"lastName\":\"Wang\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Roy\",\"lastName\":\"Zhou\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Ping\",\"lastName\":\"Zhao\",\"age\":30,\"male\":false},{\"id\":4,\"firstName\":\"Dennis\",\"lastName\":\"Kuang\",\"age\":35,\"male\":false}]}"
  val fullTeamWithDelJson = "{\"description\":\"Scala Team\",\"members\":[{\"id\":1,\"firstName\":\"Zhuchen\",\"lastName\":\"Wang\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Roy\",\"lastName\":\"Zhou\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Ping\",\"lastName\":\"Zhao\",\"age\":30,\"male\":false}]}"
  val fullTeamWithAddJson = "{\"description\":\"Scala Team\",\"members\":[{\"id\":1,\"firstName\":\"Zhuchen\",\"lastName\":\"Wang\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Roy\",\"lastName\":\"Zhou\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Ping\",\"lastName\":\"Zhao\",\"age\":30,\"male\":false},{\"id\":4,\"firstName\":\"Dennis\",\"lastName\":\"Kuang\",\"age\":35,\"male\":false},{\"id\":5,\"firstName\":\"Leon\",\"lastName\":\"Ma\",\"age\":35,\"male\":true}]}"

  val fullTeamWithDel = Team("Scala Team", List[Employee](
    Employee(1, "Zhuchen", "Wang", 20, male = true),
    Employee(2, "Roy", "Zhou", 25, male = true),
    Employee(3, "Ping", "Zhao", 30, male = false)
  ))

  val fullTeamWithAdd = Team("Scala Team", List[Employee](
    Employee(1, "Zhuchen", "Wang", 20, male = true),
    Employee(2, "Roy", "Zhou", 25, male = true),
    Employee(3, "Ping", "Zhao", 30, male = false),
    Employee(4, "Dennis", "Kuang", 35, male = false),
    newTeamMember
  ))

  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._
  //  import scala.concurrent.ExecutionContext.Implicits.global
  import DummyService._

  def startDummyService(implicit system: ActorSystem, address: String = dummyServiceIpAddress, port: Int = dummyServicePort) {
    implicit val ec = system.dispatcher
    startServer(address, port = port) {
      pathSingleSlash {
        redirect("/view", StatusCodes.Found)
      } ~
        //get, head, options
        path("view") {
          (get | head | options | post) {
            respondWithMediaType(MediaTypes.`application/json`)
            headerValueByName("req1-name") {
              value =>
                respondWithHeader(RawHeader("res-req1-name", "res-" + value)) {
                  complete {
                    fullTeam
                  }
                }
            } ~
              headerValueByName("req2-name") {
                value =>
                  respondWithHeader(RawHeader("res-req2-name", "res-" + value)){
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
        path("stop") {
          (post | parameter('method ! "post")) {
            complete {
              system.scheduler.scheduleOnce(1.second)(system.shutdown())(system.dispatcher)
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
                respondWithMediaType(MediaTypes.`application/json`)
                complete {
                  Team(fullTeam.description, fullTeam.members :+ employee)
                }
            }
          }
        } ~
        //del
        path("del" / LongNumber) {
          id =>
            delete {
              respondWithMediaType(MediaTypes.`application/json`)
              complete {
                val employee = fullTeam.members.find(_.id == id)
                employee match {
                  case Some(emp) => Team(fullTeam.description, fullTeam.members.filterNot(_ == emp))
                  case None => "cannot find the employee"
                }
              }
            }
        }
    }(system, null, bindingTimeout = 30 seconds).onComplete {
      case Success(b) =>
        println(s"Successfully bound to ${b.localAddress}")
      case Failure(ex) =>
        println(ex.getMessage)
        system.shutdown()
    }
  }
}

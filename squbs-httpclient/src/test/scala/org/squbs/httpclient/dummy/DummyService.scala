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

import java.util

import org.json4s.CustomSerializer
import org.json4s.JsonAST._
import org.squbs.httpclient.japi.{TeamBean, EmployeeBean}
import org.squbs.httpclient.json.JsonProtocol
import spray.routing.SimpleRoutingApp
import akka.actor.ActorSystem
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import scala.language.postfixOps
import spray.http._
import spray.http.HttpHeaders.RawHeader
import spray.util.Utils._


//case class reference case class
case class Employee(id: Long, firstName: String, lastName: String, age: Int, male: Boolean)

case class Team(description: String, members: List[Employee])

//non case class with accessor
class Employee1(val id: Long, val firstName: String, val lastName: String, val age: Int, val male: Boolean){
  override def equals(obj : Any) : Boolean  = {
    obj match {
      case t : Employee1 =>
        t.id == id && t.firstName == firstName && t.lastName == lastName && t.age == age && t.male == male
      case _ => false
    }
  }
}

class Team1(val description: String, val members: List[Employee1]){
  override def equals(obj : Any) : Boolean  = {
    obj match {
      case t : Team1 =>
        t.description == description && t.members == members
      case _ => false
    }
  }
}

object EmployeeBeanSerializer extends CustomSerializer[EmployeeBean](format => ( {
  case JObject(JField("id", JInt(i)) :: JField("firstName", JString(f)) :: JField("lastName", JString(l)) :: JField("age", JInt(a)) :: JField("male", JBool(m)) :: Nil) =>
    new EmployeeBean(i.longValue, f, l, a.intValue(), m)
}, {
  case x: EmployeeBean =>
    JObject(
      JField("id", JInt(BigInt(x.getId))) ::
        JField("firstName", JString(x.getFirstName)) ::
        JField("lastName", JString(x.getLastName)) ::
        JField("age", JInt(x.getAge)) ::
        JField("male", JBool(x.isMale)) ::
        Nil)
}
  ))

//scala class reference java class
class Team2(val description: String, val members: List[EmployeeBean])

object DummyService {

  val (dummyServiceIpAddress, dummyServicePort) = temporaryServerHostnameAndPort()

  val dummyServiceEndpoint = s"http://$dummyServiceIpAddress:$dummyServicePort"
}

object DummyServiceMain extends App with DummyService {
  implicit val actorSystem = ActorSystem("DummyServiceMain")
  startDummyService(actorSystem, address = "localhost", port = 8888)
}

trait DummyService extends SimpleRoutingApp {

  val fullTeamBean = {
    val list = new util.ArrayList[EmployeeBean]()
    list.add(new EmployeeBean(1, "John", "Doe", 20, true))
    list.add(new EmployeeBean(2, "Mike", "Moon", 25, true))
    list.add(new EmployeeBean(3, "Jane", "Williams", 30, false))
    list.add(new EmployeeBean(4, "Liz", "Taylor", 35, false))

    new TeamBean("squbs Team", list)
  }

  val fullTeam1 = new Team1("squbs Team", List[Employee1](
    new Employee1(1, "John", "Doe", 20, male = true),
    new Employee1(2, "Mike", "Moon", 25, male = true),
    new Employee1(3, "Jane", "Williams", 30, male = false),
    new Employee1(4, "Liz", "Taylor", 35, male = false)
  ))

  val fullTeam2 = new Team2("squbs Team", List[EmployeeBean](
    new EmployeeBean(1, "John", "Doe", 20, true),
    new EmployeeBean(2, "Mike", "Moon", 25, true),
    new EmployeeBean(3, "Jane", "Williams", 30, false),
    new EmployeeBean(4, "Liz", "Taylor", 35, false)
  ))

  val fullTeam = Team("squbs Team", List[Employee](
    Employee(1, "John", "Doe", 20, male = true),
    Employee(2, "Mike", "Moon", 25, male = true),
    Employee(3, "Jane", "Williams", 30, male = false),
    Employee(4, "Liz", "Taylor", 35, male = false)
  ))

  val newTeamMember = Employee(5, "Jack", "Ripper", 35, male = true)
  val newTeamMemberBean = new EmployeeBean(5, "Jack", "Ripper", 35, true)

  val fullTeamJson = "{\"description\":\"squbs Team\",\"members\":[{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Mike\",\"lastName\":\"Moon\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Jane\",\"lastName\":\"Williams\",\"age\":30,\"male\":false},{\"id\":4,\"firstName\":\"Liz\",\"lastName\":\"Taylor\",\"age\":35,\"male\":false}]}"
  val fullTeamWithDelJson = "{\"description\":\"squbs Team\",\"members\":[{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Mike\",\"lastName\":\"Moon\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Jane\",\"lastName\":\"Williams\",\"age\":30,\"male\":false}]}"
  val fullTeamWithAddJson = "{\"description\":\"squbs Team\",\"members\":[{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Mike\",\"lastName\":\"Moon\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Jane\",\"lastName\":\"Williams\",\"age\":30,\"male\":false},{\"id\":4,\"firstName\":\"Liz\",\"lastName\":\"Taylor\",\"age\":35,\"male\":false},{\"id\":5,\"firstName\":\"Jack\",\"lastName\":\"Ripper\",\"age\":35,\"male\":true}]}"

  val fullTeamWithDel = Team("squbs Team", List[Employee](
    Employee(1, "John", "Doe", 20, male = true),
    Employee(2, "Mike", "Moon", 25, male = true),
    Employee(3, "Jane", "Williams", 30, male = false)
  ))

  val fullTeamWithAdd = Team("squbs Team", List[Employee](
    Employee(1, "John", "Doe", 20, male = true),
    Employee(2, "Mike", "Moon", 25, male = true),
    Employee(3, "Jane", "Williams", 30, male = false),
    Employee(4, "Liz", "Taylor", 35, male = false),
    newTeamMember
  ))

  val fullTeamBeanWithAdd = fullTeamBean.addMember(newTeamMemberBean)


  //import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol.json4sUnmarshaller
  import JsonProtocol.manifestToUnmarshaller
  import JsonProtocol.manifestToMarshaller

  //import JsonProtocol.toResponseMarshallable
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
        //get, head, options
        path("viewj") {
          (get | head | options | post) {
            respondWithMediaType(MediaTypes.`application/json`)
              complete {
                fullTeamBean
              }
          }
        } ~
        path("view1") {
          (get | head | options | post) {
            respondWithMediaType(MediaTypes.`application/json`)
            complete {
              fullTeam1
            }
          }
        } ~
        path("view2") {
          (get | head | options | post) {
            respondWithMediaType(MediaTypes.`application/json`)
            complete {
              fullTeam2
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
        path("addj") {
          (post | put) {
            entity[EmployeeBean](as[EmployeeBean]) {
              employee: EmployeeBean =>
                respondWithMediaType(MediaTypes.`application/json`)
                complete {
                  fullTeamBean.addMember(employee)
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

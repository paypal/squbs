package org.squbs.httpclient.dummy

import spray.routing.SimpleRoutingApp
import akka.actor.ActorSystem
import scala.util.{Failure, Success}
import scala.concurrent.duration._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import java.net.InetAddress
import org.squbs.testkit.util.Ports

/**
* Created by hakuang on 7/10/2014.
*/

case class Employee(id: Long, firstName: String, lastName: String, age: Int, male: Boolean)

case class Team(description: String, members: List[Employee])

object DummyService {
  val dummyServiceIpAddress = InetAddress.getLocalHost.getHostAddress

  val dummyServicePort = Ports.available(8888, 9999)

  val dummyServiceEndpoint = s"http://$dummyServiceIpAddress:$dummyServicePort"
}

trait DummyService extends SimpleRoutingApp {

  val fullTeam = Team("Scala Team", List[Employee](
    Employee(1, "Zhuchen", "Wang", 20, true),
    Employee(2, "Roy", "Zhou", 25, true),
    Employee(3, "Ping", "Zhao", 30, false),
    Employee(4, "Dennis", "Kuang", 35, false)
  ))

  val newTeamMember = Employee(5, "Leon", "Ma", 35, true)
  
  val fullTeamJson = "{\"description\":\"Scala Team\",\"members\":[{\"id\":1,\"firstName\":\"Zhuchen\",\"lastName\":\"Wang\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Roy\",\"lastName\":\"Zhou\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Ping\",\"lastName\":\"Zhao\",\"age\":30,\"male\":false},{\"id\":4,\"firstName\":\"Dennis\",\"lastName\":\"Kuang\",\"age\":35,\"male\":false}]}"
  val fullTeamWithDelJson = "{\"description\":\"Scala Team\",\"members\":[{\"id\":1,\"firstName\":\"Zhuchen\",\"lastName\":\"Wang\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Roy\",\"lastName\":\"Zhou\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Ping\",\"lastName\":\"Zhao\",\"age\":30,\"male\":false}]}"
  val fullTeamWithAddJson = "{\"description\":\"Scala Team\",\"members\":[{\"id\":1,\"firstName\":\"Zhuchen\",\"lastName\":\"Wang\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Roy\",\"lastName\":\"Zhou\",\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Ping\",\"lastName\":\"Zhao\",\"age\":30,\"male\":false},{\"id\":4,\"firstName\":\"Dennis\",\"lastName\":\"Kuang\",\"age\":35,\"male\":false},{\"id\":5,\"firstName\":\"Leon\",\"lastName\":\"Ma\",\"age\":35,\"male\":true}]}"

  val fullTeamWithDel = Team("Scala Team", List[Employee](
    Employee(1, "Zhuchen", "Wang", 20, true),
    Employee(2, "Roy", "Zhou", 25, true),
    Employee(3, "Ping", "Zhao", 30, false)
  ))

  val fullTeamWithAdd = Team("Scala Team", List[Employee](
    Employee(1, "Zhuchen", "Wang", 20, true),
    Employee(2, "Roy", "Zhou", 25, true),
    Employee(3, "Ping", "Zhao", 30, false),
    Employee(4, "Dennis", "Kuang", 35, false),
    newTeamMember
  ))

  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._
  import scala.concurrent.ExecutionContext.Implicits.global
  import DummyService._

  def startDummyService(implicit system: ActorSystem, port: Int = dummyServicePort) {
    startServer(dummyServiceIpAddress, port = port) {
      pathSingleSlash {
        redirect("/view", StatusCodes.Found)
      } ~
      //get, head, options
        path("view") {
          (get | head | options) {
            respondWithMediaType(MediaTypes.`application/json`)
            headerValueByName("req1-name") {
              value =>
                respondWithHeader(RawHeader("res-req1-name", "res-" + value))
                complete {
                  fullTeam
                }
            } ~
              headerValueByName("req2-name") {
                value =>
                  respondWithHeader(RawHeader("res-req2-name", "res-" + value))
                  complete {
                    fullTeam
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
                  case Some(employee) => Team(fullTeam.description, fullTeam.members.filterNot(_ == employee))
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

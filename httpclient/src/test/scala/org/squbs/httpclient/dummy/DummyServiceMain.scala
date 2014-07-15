//package org.squbs.hc.dummy
//
//import spray.routing.SimpleRoutingApp
//import spray.http.{MediaTypes, StatusCodes}
//import akka.actor.ActorSystem
//import scala.util.{Failure, Success}
//import scala.concurrent.duration._
//import spray.httpx.unmarshalling._
//import spray.util._
//import spray.http._
//
//
///**
// * Created by hakuang on 7/10/2014.
// */
//
//case class Employee(id: Long, firstName: String, lastName: String, age: Int, male: Boolean)
//
//case class Team(description: String, members: List[Employee])
//
//object DummyServiceMain extends App with SimpleRoutingApp {
//  implicit val system = ActorSystem("DummyServiceMain")
//
//  import system.dispatcher
//
//  val defaultTeam = Team("Scala Team", List[Employee](
//    Employee(1, "Zhuchen", "Wang", 20, true),
//    Employee(2, "Roy", "Zhou", 25, true),
//    Employee(3, "Ping", "Zhao", 30, false),
//    Employee(4, "Dennis", "Kuang", 35, false)
//  ))
//
//  import org.squbs.client.json.Json4sJacksonNoTypeHintsProtocol._
//
//  startServer("localhost", port = 9999) {
//    pathSingleSlash {
//      redirect("/view", StatusCodes.Found)
//    } ~
//    path("view") {
//      (get | head | options) {
//        respondWithMediaType(MediaTypes.`application/json`)
//        complete {
//          defaultTeam
//        }
//      }
//    } ~
//    path("stop") {
//      (post | parameter('method ! "post")) {
//        complete {
//          system.scheduler.scheduleOnce(1.second)(system.shutdown())(system.dispatcher)
//          "Shutting down in 1 second..."
//        }
//      }
//    } ~
//    path("add") {
//      (post | put) {
//        entity[as[Employee]] { employee: Employee =>
//          respondWithMediaType(MediaTypes.`application/json`)
//          complete {
//            Team(defaultTeam.description, defaultTeam.members :+ employee)
//          }
//        }
//      }
//    } ~
//    path("del" / LongNumber) { id =>
//      delete {
//        respondWithMediaType(MediaTypes.`application/json`)
//        complete {
//          val employee = defaultTeam.members.find(_.id == id)
//          employee match {
//            case Some(employee) => Team(defaultTeam.description, defaultTeam.members.filterNot(_ == employee))
//            case None => "cannot find the employee"
//          }
//        }
//      }
//
//    }
//  }.onComplete {
//    case Success(b) =>
//      println(s"Successfully bound to ${b.localAddress}")
//    case Failure(ex) =>
//      println(ex.getMessage)
//      system.shutdown()
//  }
//}

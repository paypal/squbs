//package org.squbs.hc.actor
//
//import akka.actor.{ActorSystem, Props, Actor}
//import org.squbs.hc.actor.HttpClientMessage.{HttpClientMsg, CreateOrGetHttpClient}
//import org.squbs.hc.routing.{RoutingDefinition, RoutingRegistry}
//import org.squbs.hc.{HttpResponseWrapper, HttpClient}
//import org.squbs.hc.config.Configuration
//import org.squbs.hc.pipeline.PipelineDefinition
//import spray.http.HttpMethods
//import akka.pattern._
//
///**
// * Created by hakuang on 6/18/2014.
// */
//object HttpClientActorMain extends App{
//
//  implicit val system = ActorSystem("HttpClientActorMain")
//
//  RoutingRegistry.register(new GoogleRoutingDefinition())
//
//  val testActor = system.actorOf(Props(classOf[HttpClientActorTest]), "httpClientActorTest")
//  val result = testActor ? GoogleAPI
//}
//
//class HttpClientActorTest extends Actor {
//
//  val hcActor = context.actorOf(Props(classOf[HttpClientActor]), "httpClientActor")
//  val hcCallActor = context.actorOf(Props(classOf[HttpCallActor]), "httpCallActor")
//  var client = context.sender
//
//  override def receive: Receive = {
//    case GoogleAPI =>
//      client = context.sender
//      hcActor ! CreateOrGetHttpClient("googlemap")
//    case HttpClient(name, endpoint, config, pipelineDefinition) =>
//      hcCallActor ! HttpClientMsg(name, HttpMethods.GET, "/api/elevation/json?locations=27.988056,86.925278&sensor=false", None)
//    case hr @ HttpResponseWrapper(status, content) =>
//      self ! (hr, client)
//  }
//}
//
//class GoogleRoutingDefinition extends RoutingDefinition {
//  override def resolve(svcName: String, env: Option[String]): Option[String] = {
//    if (svcName == name)
//      Some("http://maps.googleapis.com/maps")
//    else
//      None
//  }
//
//  override def name: String = "googlemap"
//}
//
//case object GoogleAPI

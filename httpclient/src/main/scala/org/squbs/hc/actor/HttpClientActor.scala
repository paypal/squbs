//package org.squbs.hc.actor
//
//import akka.actor.Actor
//import org.squbs.hc.actor.HttpClientMessage._
//import org.squbs.hc.{HttpClientSupport, HttpClient}
//import spray.http.{Uri, HttpMethods}
//import akka.pattern._
//import org.squbs.hc.actor.HttpClientMessage.CreateOrGetHttpClient
//import org.squbs.hc.actor.HttpClientMessage.HttpClientMsg
//import org.squbs.hc.actor.HttpClientMessage.DeleteHttpClient
//import scala.Some
//import akka.io.IO
//import spray.can.Http
//import org.squbs.hc.routing.RoutingRegistry
//import spray.client.pipelining
//import org.squbs.hc.pipeline.EmptyPipelineDefinition
//
///**
// * Created by hakuang on 6/17/2014.
// */
//class HttpClientActor(httpClient: HttpClient) extends Actor with HttpClientSupport{
//  import scala.concurrent.ExecutionContext.Implicits.global
//
//  override def receive: Receive = {
//    case HttpGetMsg(HttpMethods.GET, uri)     => get(uri)(context.system).pipeTo(sender)
//    case HttpGetMsg(HttpMethods.HEAD, uri)    => head(uri)(context.system).pipeTo(sender)
//    case HttpGetMsg(HttpMethods.DELETE, uri)  => delete(uri)(context.system).pipeTo(sender)
//    case HttpGetMsg(HttpMethods.OPTIONS, uri) => options(uri)(context.system).pipeTo(sender)
//  }
//
//  override def httpClientInstance: HttpClient = httpClient
//}
//
//class HttpClientAsyncActor extends Actor {
//  override def receive: Actor.Receive = {
//    case HttpAsyncMsg(svcName, env, config, pipeline, HttpMethods.GET, uri) =>
//      val uri = Uri(RoutingRegistry.resolve(svcName, env).getOrElse(""))
//      val host = uri.authority.host.toString
//      val port = if (uri.effectivePort == 0) 80 else uri.effectivePort
//      val isSecure = uri.scheme.toLowerCase.equals("https")
//      val defaultHostConnectorSetup = Http.HostConnectorSetup(host, port, isSecure)
//      val setup = config map {configuration =>
//        defaultHostConnectorSetup.copy(settings = configuration.hostConfig.hostSettings, connectionType = configuration.hostConfig.connectionType)
//      } match {
//        case None => defaultHostConnectorSetup
//        case Some(hostConnectorSetup) => hostConnectorSetup
//      }
//      IO(Http) ! setup
//    case Http.HostConnectorInfo(connector, _) =>
//      val sendReceive = pipelining.sendReceive(connector)
//      val pipelines = pipeline.getOrElse(EmptyPipelineDefinition)
//      val reqPipelines = pipelines.requestPipelines
//      val resPipelines = pipelines.responsePipelines
//  }
//}
//
////class HttpCallActor extends Actor {
////  import scala.concurrent.ExecutionContext.Implicits.global
////  override def receive: Actor.Receive = {
////    case HttpClientMsg(svcName, HttpMethods.GET, uri, _) =>
////      val httpClient = HttpClient.get.get(svcName)
////      httpClient match {
////        case None => sender ! "svcName cannot be resolved by HttpClient"
////        case Some(hc) => hc.get(uri)(context.system).pipeTo(sender)
////      }
////    case HttpClientMsg(svcName, HttpMethods.HEAD, uri, _) =>
////      val httpClient = HttpClient.get.get(svcName)
////      httpClient match {
////        case None => sender ! "svcName cannot be resolved by HttpClient"
////        case Some(hc) => hc.head(uri)(context.system).pipeTo(sender)
////      }
////    case HttpClientMsg(svcName, HttpMethods.DELETE, uri, _) =>
////      val httpClient = HttpClient.get.get(svcName)
////      httpClient match {
////        case None => sender ! "svcName cannot be resolved by HttpClient"
////        case Some(hc) => hc.delete(uri)(context.system).pipeTo(sender)
////      }
////    case HttpClientMsg(svcName, HttpMethods.OPTIONS, uri, _) =>
////      val httpClient = HttpClient.get.get(svcName)
////      httpClient match {
////        case None => sender ! "svcName cannot be resolved by HttpClient"
////        case Some(hc) => hc.options(uri)(context.system).pipeTo(sender)
////      }
//////    case HttpClientMsg(svcName, HttpMethods.POST, uri, content) =>
//////      val httpClient = HttpClient.get.get(svcName)
//////      httpClient match {
//////        case None => sender ! "svcName cannot be resolved by HttpClient"
//////        case Some(hc) => content match {
//////          case Some(c) => hc.post(uri, Some(c))(context.system).pipeTo(sender)
//////          case None => sender ! "Content should not be null if you do POST"
//////        }
//////      }
//////    case HttpClientMsg(svcName, HttpMethods.PUT, uri, content) =>
//////      val httpClient = HttpClient.get.get(svcName)
//////      httpClient match {
//////        case None => sender ! "svcName cannot be resolved by HttpClient"
//////        case Some(hc) => content match {
//////          case Some(c) => hc.put(uri, Some(c))(context.system).pipeTo(sender)
//////          case None => sender ! "Content should not be null if you do POST"
//////        }
//////      }
////    case HttpClientMsg(svcName, httpMethod, uri, content) =>
////      sender ! s"$httpMethod haven't supported by HttpClient"
////  }
////}

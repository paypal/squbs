//package org.squbs.hc.actor
//
//import org.squbs.hc.config.Configuration
//import org.squbs.hc.pipeline.PipelineDefinition
//import spray.httpx.marshalling.Marshaller
//import spray.http.HttpMethod
//import akka.actor.ActorSystem
//import spray.httpx.unmarshalling._
//import org.squbs.hc.config.Configuration
//
///**
// * Created by hakuang on 6/18/2014.
// */
//object HttpClientMessage {
//
//  case class CreateOrGetHttpClient(svcName: String, env: Option[String] = None, config: Option[Configuration] = None, pipeline: Option[PipelineDefinition] = None)
//  case class DeleteHttpClient(svcName: String)
//  case object ClearHttpClient
//  case object GetAllHttpClient
//  case class HttpClientMsg[T: Marshaller](svcName: String, httpMethod: HttpMethod, uri: String, content: Option[T])(implicit system: ActorSystem)
//  case class HttpClientEntityMsg[T: Marshaller, R: FromResponseUnmarshaller](svcName: String, httpMethod: HttpMethod, uri: String, content: Option[T])(implicit system: ActorSystem)
//  case class HttpGetMsg(httpMethod: HttpMethod, uri: String)
//  case class HttpAsyncMsg(svcName: String, env: Option[String] = None, config: Option[Configuration] = None, pipeline: Option[PipelineDefinition] = None, httpMethod: HttpMethod, uri: String)
//}

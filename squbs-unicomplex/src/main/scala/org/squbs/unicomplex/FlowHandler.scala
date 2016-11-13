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

package org.squbs.unicomplex

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern._
import akka.stream.FlowShape
import akka.stream.scaladsl._
import akka.util.Timeout
import org.squbs.pipeline.streaming.{Context, PipelineExtension, PipelineSetting, RequestContext}

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.util.{Success, Try}

object FlowHandler {

  def apply(routes: Seq[(Path, FlowWrapper, PipelineSetting)], localPort: Option[Int])
           (implicit system: ActorSystem): FlowHandler = {
    new FlowHandler(routes, localPort)
  }

  def pathMatch(path: Path, target: Path): Boolean = {
    if(path.length < target.length) { false }
    else {
      @tailrec
      def innerMatch(path: Path, target:Path):Boolean = {
        if (target.isEmpty) { true }
        else {
          target.head.equals(path.head) match {
            case true => innerMatch(path.tail, target.tail)
            case _ => false
          }
        }
      }
      innerMatch(path, target)
    }
  }
}

class FlowHandler(routes: Seq[(Path, FlowWrapper, PipelineSetting)], localPort: Option[Int])
                 (implicit system: ActorSystem) {

  import FlowHandler._

  val akkaHttpConfig = system.settings.config.getConfig("akka.http")

  def flow: Flow[HttpRequest, HttpResponse, Any] = dispatchFlow

  val pipelineExtension = PipelineExtension(system)

  import system.dispatcher

  def normPath(path: Path): Path = if (path.startsWithSlash) path.tail else path

  // TODO FIX ME - Discuss with Akara and Qian.
  // I am not sure what exactly the timeout should be set to.  One option is to use akka.http.server.request-timeout; however,
  // that will be available in the next release: https://github.com/akka/akka/issues/16819.
  // Even then, I am not sure if that would be the right value..
  import scala.concurrent.duration._
  implicit val askTimeOut: Timeout = 5 seconds
  private def asyncHandler(routeActor: ActorRef) = (req: HttpRequest) => (routeActor ? req).mapTo[HttpResponse]
  def runRoute(routeActor: ActorRef, rc: RequestContext) = asyncHandler(routeActor)(rc.request) map {
    httpResponse => rc.copy(response = Option(Try(httpResponse)))
  }

  val NotFound = HttpResponse(StatusCodes.NotFound, entity = StatusCodes.NotFound.defaultMessage)
  val InternalServerError = HttpResponse(StatusCodes.InternalServerError,
                                         entity = StatusCodes.InternalServerError.defaultMessage)

  def toRequestContextFlow(myFlow: Flow[HttpRequest, HttpResponse, NotUsed]):
      Flow[RequestContext, RequestContext, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val unzip = b.add(UnzipWith[RequestContext, RequestContext, HttpRequest] { rc => (rc, rc.request)})
      val zip = b.add(ZipWith[RequestContext, HttpResponse, RequestContext] {
        case (rc, resp) => rc.copy(response = Some(Try(resp)))
      })
      unzip.out0 ~> zip.in0
      unzip.out1 ~> myFlow ~> zip.in1
      FlowShape(unzip.in, zip.out)
    })

  lazy val routeFlow =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val (paths, flowWrappers, pipelineSettings) = routes unzip3

      val routesMerge = b.add(Merge[RequestContext](flowWrappers.size + 1))

      def partitioner(rc: RequestContext) = {
        val index = paths.indexWhere(p => pathMatch(normPath(rc.request.uri.path), p))
        // If no match, give the last port's index (which is the NOT_FOUND use case)
        if(index >= 0) index else paths.size
      }

      val partition = b.add(Partition(paths.size + 1, partitioner))

      flowWrappers.zipWithIndex foreach { case (fw, i) =>
        val serviceFlow = toRequestContextFlow(fw.flow)

        pipelineExtension.getFlow(pipelineSettings(i), Context(name = paths(i).toString())) match {
          case Some(pipeline) => partition.out(i) ~> pipeline.join(serviceFlow) ~> routesMerge
          case None => partition.out(i) ~> serviceFlow ~> routesMerge
        }
      }

      val notFound = b.add(Flow[RequestContext].map {
        rc => rc.copy(response = Option(Try(NotFound)))
      })

      // Last output port is for 404
      partition.out(flowWrappers.size) ~> notFound ~> routesMerge

      FlowShape(partition.in, routesMerge.out)
  })

  lazy val dispatchFlow: Flow[HttpRequest, HttpResponse, Any] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      object RequestContextOrdering extends Ordering[RequestContext] {
        def compare(x: RequestContext, y: RequestContext) = x.httpPipeliningOrder compare y.httpPipeliningOrder
      }

      val httpPipeliningOrder = b.add(
        new OrderingStage[RequestContext, Int](0, (x: Int) => x + 1,
                                               (rc: RequestContext) => rc.httpPipeliningOrder)
                                               (RequestContextOrdering))

      val responseFlow = b.add(Flow[RequestContext].map { rc =>
        rc.response map {
          case Success(response) => response
          case _ => InternalServerError
        } getOrElse NotFound
      })

      val zipF = localPort map { port =>
        if (port != 0) {
          // Port is configured, we use the configured port.
          (hr: HttpRequest, po: Int) => RequestContext(hr, po).addRequestHeaders(LocalPortHeader(port))
        } else {
          // Else we use the port from the URI.
          (hr: HttpRequest, po: Int) => RequestContext(hr, po).addRequestHeaders(LocalPortHeader(hr.uri.effectivePort))
        }
      } getOrElse { (hr: HttpRequest, po: Int) => RequestContext(hr, po) }

      val zip = b.add(ZipWith[HttpRequest, Int, RequestContext](zipF))

      // Generate id for each request to order requests for  Http Pipelining
      Source.fromIterator(() => Iterator.from(0)) ~> zip.in1
      zip.out ~> routeFlow ~> httpPipeliningOrder ~> responseFlow

      // expose ports
      FlowShape(zip.in0, responseFlow.out)
    })
}
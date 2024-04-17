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

package org.squbs.unicomplex

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.stream.{FlowShape, Materializer}
import org.apache.pekko.stream.scaladsl._
import org.squbs.pipeline.{Context, PipelineExtension, PipelineSetting, RequestContext, ServerPipeline}
import org.squbs.streams.BoundedOrdering

import scala.annotation.tailrec
import scala.language.postfixOps
import scala.util.{Success, Try}

object FlowHandler {

  def apply(routes: Seq[(Path, FlowWrapper, PipelineSetting)], localPort: Option[Int])
           (implicit system: ActorSystem, materializer: Materializer): FlowHandler = {
    new FlowHandler(routes, localPort)
  }

  def pathMatch(path: Path, target: Path): Boolean = {
    if(path.length < target.length) { false }
    else {
      @tailrec
      def innerMatch(path: Path, target: Path): Boolean =
        if (target.isEmpty) true
        else if (target.head == path.head) innerMatch(path.tail, target.tail)
        else false

      innerMatch(path, target)
    }
  }
}

class FlowHandler(routes: Seq[(Path, FlowWrapper, PipelineSetting)], localPort: Option[Int])
                 (implicit system: ActorSystem, materializer: Materializer) {

  import FlowHandler._

  val pipelineLimit: Int = system.settings.config.getInt("pekko.http.server.pipelining-limit")

  def flow: Flow[HttpRequest, HttpResponse, Any] = dispatchFlow

  val pipelineExtension = PipelineExtension(system)

  def normPath(path: Path): Path = if (path.startsWithSlash) path.tail else path

  val NotFound = HttpResponse(StatusCodes.NotFound, entity = StatusCodes.NotFound.defaultMessage)
  val InternalServerError = HttpResponse(StatusCodes.InternalServerError,
                                         entity = StatusCodes.InternalServerError.defaultMessage)

  lazy val routeFlow: Flow[RequestContext, RequestContext, NotUsed] =
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
        val pathString = paths(i).toString
        val wc = if(pathString.isEmpty) "/" else pathString
        pipelineExtension.getFlow(pipelineSettings(i), Context(wc, ServerPipeline)) match {
          case Some(pipeline) => partition.out(i) ~> pipeline.join(fw.flow(materializer)) ~> routesMerge
          case None => partition.out(i) ~> fw.flow(materializer) ~> routesMerge
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

      val httpPipeliningOrder =
        b.add(BoundedOrdering[RequestContext, Long](pipelineLimit, 0L, _ + 1L, _.httpPipeliningOrder))

      val responseFlow = b.add(Flow[RequestContext].map { rc =>
        rc.response map {
          case Success(response) => response
          case _ => InternalServerError
        } getOrElse NotFound
      })

      val zipF: (HttpRequest, Long) => RequestContext = localPort map { port =>
        if (port != 0) {
          // Port is configured, we use the configured port.
          (hr: HttpRequest, po: Long) => RequestContext(hr, po).withRequestHeader(LocalPortHeader(port))
        } else {
          // Else we use the port from the URI.
          (hr: HttpRequest, po: Long) => RequestContext(hr, po).withRequestHeader(LocalPortHeader(hr.uri.effectivePort))
        }
      } getOrElse { (hr: HttpRequest, po: Long) => RequestContext(hr, po) }

      val zip = b.add(ZipWith[HttpRequest, Long, RequestContext](zipF))

      // Generate id for each request to order requests for Http Pipelining
      Source.fromIterator(() => Iterator.iterate(0L)(_ + 1L)) ~> zip.in1
      zip.out ~> routeFlow ~> httpPipeliningOrder ~> responseFlow

      // expose ports
      FlowShape(zip.in0, responseFlow.out)
    })
}

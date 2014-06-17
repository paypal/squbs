package org.squbs.hc.pipeline.impl

import org.squbs.hc.pipeline.RequestPipelineHandler
import spray.client.pipelining
import spray.http.HttpHeader

/**
 * Created by hakuang on 6/5/2014.
 */
case class RequestAddHeaderHandler(httpHeader: HttpHeader) extends RequestPipelineHandler {
  override def processRequest: pipelining.RequestTransformer = { httpRequest =>
      val originalHeaders = httpRequest.headers
      httpRequest.copy(headers = (originalHeaders :+ httpHeader))
  }
}

case class RequestRemoveHeaderHandler(httpHeader: HttpHeader) extends RequestPipelineHandler {
  override def processRequest: pipelining.RequestTransformer = { httpRequest =>
    val originalHeaders = httpRequest.headers.groupBy[Boolean](_.name == httpHeader.name)
    httpRequest.copy(headers = (originalHeaders.get(false).getOrElse(List.empty[HttpHeader])))
  }
}

case class RequestUpdateHeaderHandler(httpHeader: HttpHeader) extends RequestPipelineHandler {
  override def processRequest: pipelining.RequestTransformer = { httpRequest =>
    val originalHeaders = httpRequest.headers.groupBy[Boolean](_.name == httpHeader.name)
    httpRequest.copy(headers = (originalHeaders.get(false).getOrElse(List.empty[HttpHeader]) :+ httpHeader))
  }
}

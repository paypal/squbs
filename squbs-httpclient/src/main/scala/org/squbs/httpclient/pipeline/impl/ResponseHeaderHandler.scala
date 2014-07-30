package org.squbs.httpclient.pipeline.impl

import spray.http.HttpHeader
import org.squbs.httpclient.pipeline.{ResponsePipelineHandler}
import spray.client.pipelining._

/**
 * Created by hakuang on 6/9/2014.
 */
case class ResponseAddHeaderHandler(httpHeader: HttpHeader) extends ResponsePipelineHandler {
  override def processResponse: ResponseTransformer = { httpResponse =>
    val originalHeaders = httpResponse.headers
    httpResponse.copy(headers = (originalHeaders :+ httpHeader))
  }
}

case class ResponseRemoveHeaderHandler(httpHeader: HttpHeader) extends ResponsePipelineHandler {
  override def processResponse: ResponseTransformer = { httpResponse =>
    val originalHeaders = httpResponse.headers.groupBy[Boolean](_.name == httpHeader.name)
    httpResponse.copy(headers = (originalHeaders.get(false).getOrElse(List.empty[HttpHeader])))
  }
}

case class ResponseUpdateHeaderHandler(httpHeader: HttpHeader) extends ResponsePipelineHandler {
  override def processResponse: ResponseTransformer = { httpResponse =>
    val originalHeaders = httpResponse.headers.groupBy[Boolean](_.name == httpHeader.name)
    httpResponse.copy(headers = (originalHeaders.get(false).getOrElse(List.empty[HttpHeader]) :+ httpHeader))
  }
}
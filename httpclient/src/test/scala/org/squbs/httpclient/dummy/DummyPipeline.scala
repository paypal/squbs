package org.squbs.httpclient.dummy

import org.squbs.httpclient.pipeline.Pipeline
import spray.client.pipelining._
import org.squbs.httpclient.pipeline.impl.{ResponseAddHeaderHandler, RequestAddHeaderHandler}
import spray.http.HttpHeaders.RawHeader

/**
 * Created by hakuang on 7/23/2014.
 */
object DummyRequestPipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("req1-name", "req1-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq.empty[ResponseTransformer]
}

object DummyResponsePipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq.empty[RequestTransformer]
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("res1-name", "res1-value")).processResponse)
}

object DummyRequestResponsePipeline extends Pipeline {
  override def requestPipelines: Seq[RequestTransformer] = Seq[RequestTransformer](RequestAddHeaderHandler(RawHeader("req2-name", "req2-value")).processRequest)
  override def responsePipelines: Seq[ResponseTransformer] = Seq[ResponseTransformer](ResponseAddHeaderHandler(RawHeader("res2-name", "res2-value")).processResponse)
}

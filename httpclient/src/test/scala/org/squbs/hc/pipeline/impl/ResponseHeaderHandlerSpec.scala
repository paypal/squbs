package org.squbs.hc.pipeline.impl

import org.scalatest.{Matchers, FlatSpec}
import spray.http.{HttpHeader, HttpResponse}
import spray.http.HttpHeaders.RawHeader

/**
  * Created by hakuang on 6/5/2014.
  */
class ResponseHeaderHandlerSpec extends FlatSpec with Matchers{

   "ResponseAddHeaderHandler" should "support to add HttpHeader in HttpRequest" in {
     val httpResponse = HttpResponse()
     val httpHeader = RawHeader("test-name", "test-value")
     val handler = ResponseAddHeaderHandler(httpHeader)
     val updateHttpResponse = handler.processResponse(httpResponse)
     updateHttpResponse.headers.length should be (1)
     updateHttpResponse.headers.head should be (httpHeader)
   }

   "ResponseRemoveHeaderHandler" should "support to remove HttpHeader in HttpRequest" in {
     val httpHeader1 = RawHeader("name1", "value1")
     val httpHeader2 = RawHeader("name2", "value2")
     val httpResponse = HttpResponse(headers = List[HttpHeader](httpHeader1, httpHeader2))
     val handler = ResponseRemoveHeaderHandler(httpHeader1)
     val updateHttpResponse = handler.processResponse(httpResponse)
     updateHttpResponse.headers.length should be (1)
     updateHttpResponse.headers.head should be (httpHeader2)
   }

   "ResponseUpdateHeaderHandler" should "support to update HttpHeader in HttpRequest" in {
     val httpHeader1 = RawHeader("name1", "value1")
     val httpHeader2 = RawHeader("name1", "value2")
     val httpResponse = HttpResponse(headers = List[HttpHeader](httpHeader1))
     val handler = ResponseUpdateHeaderHandler(httpHeader2)
     val updateHttpResponse = handler.processResponse(httpResponse)
     updateHttpResponse.headers.length should be (1)
     updateHttpResponse.headers.head should be (httpHeader2)
   }
 }

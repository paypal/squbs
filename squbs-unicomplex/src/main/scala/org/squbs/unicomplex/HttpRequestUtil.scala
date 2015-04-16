package org.squbs.unicomplex

import spray.http.HttpRequest

/**
 * Created by lma on 2015/4/16.
 */
object HttpRequestUtil {

  implicit class HttpRequestAdapter(request: HttpRequest) {

    def getWebContext: Option[String] = {
      request.headers.find(_.isInstanceOf[WebContextHeader]) match {
        case None => None
        case Some(header) => Option(header.value)
      }
    }

  }

}

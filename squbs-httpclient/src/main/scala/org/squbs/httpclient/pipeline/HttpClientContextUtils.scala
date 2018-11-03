package org.squbs.httpclient.pipeline

import org.squbs.httpclient.endpoint.Endpoint
import org.squbs.pipeline.RequestContext

/**
 * Created by jiamzhang on 2015/3/6.
 */
object HttpClientContextUtils {
	implicit class context2method(ctx: RequestContext) {
		def +> (ep: Endpoint): RequestContext = {
			ctx.copy(attributes = ctx.attributes + ("HttpClient.Endpoint" -> ep))
		}

		def getEndpoint: Option[Endpoint] = ctx.attribute[Endpoint]("HttpClient.Endpoint")
	}
}

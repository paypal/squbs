package org.squbs.httpclient.japi

import org.squbs.httpclient.endpoint.Endpoint

/**
 * Created by lma on 7/15/2015.
 */
object EndpointFactory {

  def create(uri: String) = Endpoint(uri)

}

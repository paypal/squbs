package org.squbs.pattern.spray.japi

import spray.http.{ChunkedMessageEnd, HttpHeader}

import scala.collection.JavaConversions._


/**
 * Created by lma on 7/7/2015.
 */
object ChunkedMessageEndFactory {

  def create(): ChunkedMessageEnd = ChunkedMessageEnd

  def create(extension: String) = ChunkedMessageEnd(extension)

  def create(trailer: java.util.List[HttpHeader]) = ChunkedMessageEnd(trailer = trailer.toList)

  def create(extension: String, trailer: java.util.List[HttpHeader]) = ChunkedMessageEnd(extension, trailer.toList)


}

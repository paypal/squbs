package org.squbs.pattern.spray.japi

import spray.http.{HttpCharset, MessageChunk}

/**
 * Created by lma on 7/8/2015.
 */
object MessageChunkFactory {


  def create(body: String) = MessageChunk(body)

  def create(body: String, charset: HttpCharset) = MessageChunk(body, charset)

  def create(body: String, extension: String) = MessageChunk(body, extension)

  def create(body: String, charset: HttpCharset, extension: String) = MessageChunk(body, charset, extension)

  def create(bytes: Array[Byte]) = MessageChunk(bytes)

}

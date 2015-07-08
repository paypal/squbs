package org.squbs.pattern.spray.japi

import spray.http.{ContentType, HttpEntity}

/**
 * Created by lma on 7/7/2015.
 */
object HttpEntityFactory {

  def create(string : String) = HttpEntity(string)

  def create(bytes: Array[Byte]) = HttpEntity(bytes)

  def create(contentType: ContentType, string: String) = HttpEntity(contentType, string)

  def create(contentType: ContentType, bytes: Array[Byte]) = HttpEntity(contentType, bytes)



}

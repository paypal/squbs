package org.squbs.hc.json

import spray.httpx.Json4sSupport
import org.json4s._
import org.json4s.ShortTypeHints

/**
 * Created by hakuang on 6/2/2014.
 */
object Json4sNativeNoTypeHintsProtocol extends Json4sSupport {
  override implicit def json4sFormats: Formats = DefaultFormats.withHints(NoTypeHints)
}

trait Json4sNativeShortTypeHintsProtocol extends Json4sSupport {
  override implicit def json4sFormats: Formats = DefaultFormats.withHints(ShortTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sNativeFullTypeHintsProtocol extends Json4sSupport {
  override implicit def json4sFormats: Formats = DefaultFormats.withHints(FullTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sNativeCustomProtocol extends Json4sSupport

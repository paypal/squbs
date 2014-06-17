package org.squbs.hc.json

import spray.httpx.Json4sJacksonSupport
import org.json4s._

/**
 * Created by hakuang on 6/2/2014.
 */
object Json4sJacksonNoTypeHintsProtocol extends Json4sJacksonSupport {
  override implicit def json4sJacksonFormats: Formats = DefaultFormats.withHints(NoTypeHints)
}

trait Json4sJacksonShortTypeHintsProtocol extends Json4sJacksonSupport {
  override implicit def json4sJacksonFormats: Formats = DefaultFormats.withHints(ShortTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sJacksonFullTypeHintsProtocol extends Json4sJacksonSupport {
  override implicit def json4sJacksonFormats: Formats = DefaultFormats.withHints(FullTypeHints(hints))
  def hints: List[Class[_]]
}

trait Json4sJacksonCustomProtocol extends Json4sJacksonSupport

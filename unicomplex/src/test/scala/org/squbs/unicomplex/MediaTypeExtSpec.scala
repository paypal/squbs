package org.squbs.unicomplex

import org.scalatest.{FunSpecLike, Matchers}
import spray.http.MediaType

class MediaTypeExtSpec extends FunSpecLike with Matchers {

  describe ("The MediaTypeExt") {
    it ("should provide media type text/event-stream") {
      MediaTypeExt.`text/event-stream` should be (MediaType.custom("text", "event-stream", compressible = true))
    }
  }
}

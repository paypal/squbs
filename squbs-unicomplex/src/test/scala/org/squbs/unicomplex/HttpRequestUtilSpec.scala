/*
 *  Copyright 2015 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.squbs.unicomplex

import org.scalatest.{FunSpecLike, Matchers}
import spray.http.HttpRequest

class HttpRequestUtilSpec extends FunSpecLike with Matchers {

  import org.squbs.unicomplex.HttpRequestUtil._

  describe("HttpRequestUtil") {

    it("should get web context") {

      var request = HttpRequest()
      request.getWebContext should be(None)

      request = request.copy(headers = WebContextHeader("abc") :: request.headers)
      request.getWebContext should be(Some("abc"))
    }

  }
}

/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.squbs.unicomplex

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.testkit.TestKit
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.unicomplex.FlowHandler._

class ServiceRegistrySpec extends TestKit(ActorSystem("ServiceRegistrySpec")) with AnyFlatSpecLike with Matchers {

  "merge" should "work" in {

    val serviceRegistry = new ServiceRegistry(system.log)
    import serviceRegistry.merge

    val pipelineSetting = (None, None, None)
    val result = merge(Seq(), "abc", "old", pipelineSetting)
    result should have size 1
    result(0)._2 should be ("old")

    val result1 = merge(result, "", "empty", pipelineSetting)
    result1 should have size 2
    result1(0)._2 should be ("old")
    result1(1)._2 should be ("empty")


    val result2 = merge(result1, "abc/def", "abc/def", pipelineSetting)
    result2 should have size 3
    result2(0)._2 should be ("abc/def")
    result2(1)._2 should be ("old")
    result2(2)._2 should be ("empty")

    val result3 = merge(result2, "abc", "new", pipelineSetting)
    result3 should have size 3
    result3(0)._2 should be ("abc/def")
    result3(1)._2 should be ("new")
    result3(2)._2 should be ("empty")

    val finding = result3 find {
      entry => pathMatch(Path("abc"), entry._1)
    }
    finding should not be None
    finding.get._2 should be ("new")

    val finding2 = result3 find {
      entry => pathMatch(Path("abc/def"), entry._1)
    }
    finding2 should not be None
    finding2.get._2 should be ("abc/def")

    val finding3 = result3 find {
      entry => pathMatch(Path("aabc/def"), entry._1)
    }
    finding3 should not be None
    finding3.get._2 should be ("empty")

    val finding4 = result3 find {
      entry => pathMatch(Path("abc/defg"), entry._1)
    }
    finding4 should not be None
    finding4.get._2 should be ("new")

    val finding5 = result3 find {
      entry => pathMatch(Path("abcd/a"), entry._1) // abcd should not match either abc/def nor abc
    }
    finding5.get._2 should be ("empty")
  }
}

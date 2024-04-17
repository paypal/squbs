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

package org.squbs.httpclient

import org.apache.pekko.http.scaladsl.model.Uri
import org.scalatest.OptionValues._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.squbs.resolver.ResolverRegistry
import org.squbs.testkit.CustomTestKit

class DefaultHttpEndpointResolverSpec extends CustomTestKit with AnyFlatSpecLike with Matchers {

  ResolverRegistry(system).register(new DefaultHttpEndpointResolver)

  private def resolve(uri: String) = ResolverRegistry(system).resolve[HttpEndpoint](uri).value

  it should "resolve valid http uri string to an HttpEndpoint" in {
    resolve("http://pekko.io:80") shouldBe HttpEndpoint(Uri("http://pekko.io:80"))
    resolve("http://pekko.io") shouldBe HttpEndpoint(Uri("http://pekko.io"))
  }

  it should "resolve valid https uri string to an HttpEndpoint" in {
    resolve("https://pekko.io:443") shouldBe HttpEndpoint(Uri("https://pekko.io:443"))
    resolve("https://pekko.io") shouldBe HttpEndpoint(Uri("https://pekko.io"))
  }

  it should "not resolve invalid http uri string to an HttpEndpoint" in {
    ResolverRegistry(system).resolve[HttpEndpoint]("invalidUri:") shouldBe empty
    ResolverRegistry(system).resolve[HttpEndpoint]("ftp://pekko.io") shouldBe empty
  }

  it should "set the resolver name to the class name" in {
    (new DefaultHttpEndpointResolver).name shouldEqual "org.squbs.httpclient.DefaultHttpEndpointResolver"
  }
}

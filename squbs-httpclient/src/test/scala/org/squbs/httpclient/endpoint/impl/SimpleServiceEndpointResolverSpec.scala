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

package org.squbs.httpclient.endpoint.impl

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{FlatSpecLike, BeforeAndAfterAll, Matchers}
import org.squbs.httpclient.endpoint.{Endpoint, EndpointRegistry}
import org.squbs.httpclient.{Settings, HttpClientTestKit, Configuration}
import javax.net.ssl.SSLContext

class SimpleServiceEndpointResolverSpec extends TestKit(ActorSystem("SimpleServiceEndpointResolverSpec"))
    with FlatSpecLike with HttpClientTestKit with Matchers with BeforeAndAfterAll{

  override def afterAll() = {
    clearHttpClient()
  }

  "SimpleServiceEndpointResolver" should "have the correct behaviour" in {
    val simpleResolver = SimpleServiceEndpointResolver("simple", Map[String, Configuration](
      "http://localhost:8080" -> Configuration(),
      "https://localhost:8443" -> Configuration(settings = Settings(sslContext = Some(SSLContext.getDefault)))
    ))
    simpleResolver.name should be ("simple")
    EndpointRegistry(system).register(simpleResolver)
    EndpointRegistry(system).resolve("http://localhost:8080") should be (Some(Endpoint("http://localhost:8080")))
    EndpointRegistry(system).resolve("https://localhost:8443") should be (Some(Endpoint("https://localhost:8443",
      Configuration(settings = Settings(sslContext = Some(SSLContext.getDefault))))))
    EndpointRegistry(system).resolve("notExisting") should be (None)
    EndpointRegistry(system).unregister(simpleResolver.name)
  }

  "ExternalServiceEndpointResolver with null Configuration" should "not throw out exception" in {
    val resolver = SimpleServiceEndpointResolver("external", Map("http://www.ebay.com" -> null))
    resolver.resolve("http://www.ebay.com") should be (Some(Endpoint("http://www.ebay.com")))
  }
}
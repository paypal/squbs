/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.httpclient.demo

import org.squbs.httpclient.endpoint.{EndpointRegistry, Endpoint, EndpointResolver}
import org.squbs.httpclient.env.Environment
import akka.actor.ActorSystem
import org.squbs.httpclient._
import spray.http._
import scala.util.Failure
import scala.Some
import scala.util.Success

object DeliveryServiceMain extends App with HttpClientTestKit{

  private implicit val system = ActorSystem("DeliveryServiceMain")
  import system.dispatcher
  EndpointRegistry.register(DeliveryServiceEndpointResolver)
  import org.squbs.httpclient.json.Json4sJacksonNoTypeHintsProtocol._

  val postData = DeliveryPayload(
    zoneId = 6,
    sellerId = "mobileqaseller2",
    requestId = "NO_CACHE",
    deliveryAddress = DeliveryAddress("529 5th ave", null, "new york", "NY", "10017-4608", "US", ""),
    source = "CORE",
    serviceIds = List[String]("EBN-US-IMMEDIATE","EBN-US-SAME-DAY","EBN-US-NEXT-DAY"),
    deliveryContact = DeliveryContact("cps_ios_test_wraith", "212 808 0309"),
    orderDetails = OrderDetails(List[Product](Product("BOPIS MOBILE", "MEBN ONLY TEST ITEMS – DO NOT BUY!+20140421030329", null, null, 1, Price("USD", 10.0), 1)), Price("USD", 10.0)),
    storeId = "10008",
    referenceDate = 1403804176062L)

  val response = HttpClientFactory.get("deliveryService").post[DeliveryPayload]("/buy/shipping/v2/deliveries/delivery-slots/available", postData)
  response onComplete {
    case Success(res@HttpResponse(StatusCodes.OK, _, _, _)) =>
      println("Success, response entity is: " + res.entity.asString)
      shutdownActorSystem
    case Success(res@HttpResponse(code, _, _, _)) =>
      println("Success, the status code is: " + code + ", response entity is: " + res.entity.asString)
      shutdownActorSystem
    case Failure(e) =>
      println("Failure, the reason is: " + e.getMessage)
      shutdownActorSystem
  }
}

object DeliveryServiceEndpointResolver extends EndpointResolver {
  override def resolve(svcName: String, env: Environment): Option[Endpoint] = {
    Some(Endpoint("https://svcs.ebay.com"))
  }

  override def name: String = "deliveryService"
}

case class DeliveryAddress(line1: String, line2: String, city: String, state: String, postalCode: String, country: String, notes: String)

case class DeliveryContact(name: String, phone: String)

case class Price(currency: String, amt: Double)

case class Product(sku: String, name: String, dimensions: String, weight: String, quantity: Int, price: Price, id: Int)

case class OrderDetails(products: List[Product], total: Price)

case class DeliveryPayload(zoneId: Int,
                           sellerId: String,
                           requestId: String,
                           deliveryAddress: DeliveryAddress,
                           source: String,
                           serviceIds: List[String],
                           deliveryContact: DeliveryContact,
                           orderDetails: OrderDetails,
                           storeId: String,
                           referenceDate: Long)

case class DeliveryResult(reservationToken: String, serviceType: String, start: String, end: String, validUntil: String)
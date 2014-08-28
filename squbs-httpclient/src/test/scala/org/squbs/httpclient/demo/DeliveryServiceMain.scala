package org.squbs.httpclient.demo

import org.squbs.httpclient.endpoint.{EndpointRegistry, Endpoint, EndpointResolver}
import org.squbs.httpclient.env.Environment
import akka.actor.ActorSystem
import org.squbs.httpclient._
import spray.http._
import scala.util.Failure
import scala.Some
import scala.util.Success

/**
* Created by hakuang on 8/25/14.
*/
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
//  println(write(postData))
//  val response = HttpClientFactory.get("deliveryService").post[DeliveryPayload]("/buy/shipping/v2/deliveries/delivery-slots/available", Some(postData))
//  response onComplete {
//    case Success(res@HttpResponse(StatusCodes.OK, _, _, _)) =>
//      println("Success, response entity is: " + res.entity.asString)
//      shutdownActorSystem
//    case Success(res@HttpResponse(code, _, _, _)) =>
//      println("Success, the status code is: " + code + ", response entity is: " + res.entity.asString)
//      shutdownActorSystem
//    case Failure(e) =>
//      println("Failure, the reason is: " + e.getMessage)
//      shutdownActorSystem
  val response = HttpClientFactory.get("deliveryService").postEntity[DeliveryPayload, List[DeliveryResult]]("/buy/shipping/v2/deliveries/delivery-slots/available", Some(postData))
  response onComplete {
    case Success(Result(data, HttpResponse(StatusCodes.OK, _, _, _))) =>
      data.foreach(println(_))
      shutdownActorSystem
    case Success(Result(data, HttpResponse(code, _, _, _))) =>
      println("Success, the status code is: " + code)
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
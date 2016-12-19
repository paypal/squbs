/*
 * Copyright 2015 PayPal
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

package akka.http.org.squbs.httpclient

import java.util.Optional

import akka.http.impl.util.JavaMapping
import akka.http.javadsl.{model => jm}
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.{javadsl => jd}
import akka.japi.Pair
import akka.stream.scaladsl.Flow
import akka.stream.{javadsl => js}

import scala.util.Try

/**
  * The JavaConverters are under Akka HTTP package to get access to the non-exposed converters there.
  */
object JavaConverters {
  def fromJava(connectionContext: Optional[jd.HttpsConnectionContext],
                                 settings: Optional[jd.settings.ConnectionPoolSettings]):
  (Option[HttpsConnectionContext], Option[ConnectionPoolSettings]) = {
    import scala.compat.java8.OptionConverters._
    val cCtx = connectionContext.asScala.asInstanceOf[Option[HttpsConnectionContext]]
    val sSettings = settings.asScala.asInstanceOf[Option[ConnectionPoolSettings]]
    (cCtx, sSettings)
  }

  private def adaptTupleFlow[T, Mat](scalaFlow: Flow[(HttpRequest, T), (Try[HttpResponse], T), Mat]):
  js.Flow[Pair[jm.HttpRequest, T], Pair[Try[jm.HttpResponse], T], Mat] = {
    implicit val _ = JavaMapping.identity[T]
    JavaMapping.toJava(scalaFlow)(JavaMapping.flowMapping[Pair[jm.HttpRequest, T], (HttpRequest, T),
      Pair[Try[jm.HttpResponse], T], (Try[HttpResponse], T), Mat])
  }

  def toJava[T](flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]):
  js.Flow[Pair[jm.HttpRequest, T], Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] =
    adaptTupleFlow(flow.mapMaterializedValue(_.toJava))
}

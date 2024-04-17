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

package org.apache.pekko.http.org.squbs.util

import java.util.Optional
import org.apache.pekko.NotUsed
import org.apache.pekko.http.impl.util.JavaMapping
import org.apache.pekko.http.javadsl.{model => jm}
import org.apache.pekko.http.scaladsl.Http.HostConnectionPool
import org.apache.pekko.http.scaladsl.HttpsConnectionContext
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.http.{javadsl => jd}
import org.apache.pekko.japi.Pair
import org.apache.pekko.stream.scaladsl.{BidiFlow, Flow}
import org.apache.pekko.stream.{javadsl => js}

import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

/**
  * The JavaConverters are under Pekko HTTP package to get access to the non-exposed converters there.
  */
object JavaConverters {
  def fromJava(connectionContext: Optional[jd.HttpsConnectionContext],
                                 settings: Optional[jd.settings.ConnectionPoolSettings]):
  (Option[HttpsConnectionContext], Option[ConnectionPoolSettings]) = {
    val cCtx = connectionContext.asScala.asInstanceOf[Option[HttpsConnectionContext]]
    val sSettings = settings.asScala.asInstanceOf[Option[ConnectionPoolSettings]]
    (cCtx, sSettings)
  }

  def toJava[In1, Out1, In2, Out2, Context](bidiFlow: BidiFlow[(In1, Context), (Out1, Context), (In2, Context), (Out2, Context), NotUsed]):
  js.BidiFlow[Pair[In1, Context], Pair[Out1, Context], Pair[In2, Context], Pair[Out2, Context], NotUsed] = {
    implicit val sIn1Mapping = JavaMapping.identity[In1]
    implicit val sOut1Mapping = JavaMapping.identity[Out1]
    implicit val sIn2Mapping = JavaMapping.identity[In2]
    implicit val sOut2Mapping = JavaMapping.identity[Out2]
    implicit val contextMapping = JavaMapping.identity[Context]
    val javaToScalaAdapter = JavaMapping.adapterBidiFlow[Pair[In1, Context], (In1, Context), (Out2, Context), Pair[Out2, Context]]
    val scalaToJavaAdapter = JavaMapping.adapterBidiFlow[Pair[In2, Context], (In2, Context), (Out1, Context), Pair[Out1, Context]].reversed
    javaToScalaAdapter.atop(bidiFlow).atop(scalaToJavaAdapter).asJava
  }

  private def adaptTupleFlow[T](scalaFlow: Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]):
  js.Flow[Pair[jm.HttpRequest, T], Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] = {
    implicit val jIdentityMapping = JavaMapping.identity[T]
    implicit object HostConnectionPoolMapping extends JavaMapping[jd.HostConnectionPool, HostConnectionPool] {
      def toScala(javaObject: jd.HostConnectionPool): HostConnectionPool =
        throw new UnsupportedOperationException("jd.HostConnectionPool cannot be converted to Scala")
      def toJava(scalaObject: HostConnectionPool): jd.HostConnectionPool = scalaObject.toJava
    }
    JavaMapping.toJava(scalaFlow)(JavaMapping.flowMapping[Pair[jm.HttpRequest, T], (HttpRequest, T),
      Pair[Try[jm.HttpResponse], T], (Try[HttpResponse], T), jd.HostConnectionPool, HostConnectionPool])
  }

  def toJava[T](flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]):
  js.Flow[Pair[jm.HttpRequest, T], Pair[Try[jm.HttpResponse], T], jd.HostConnectionPool] = {
    adaptTupleFlow[T](flow)
  }

  def toScala(uri: org.apache.pekko.http.javadsl.model.Uri) = JavaMapping.toScala(uri)

}

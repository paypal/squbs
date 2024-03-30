/*
 *  Copyright 2017 PayPal
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
package org.squbs.marshallers

import java.util.concurrent.CompletionStage

import org.apache.pekko.http.javadsl.marshalling.Marshaller
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.http.scaladsl.settings
import org.apache.pekko.stream.Materializer

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext

/**
  * Java API for manual marshalling and unmarshalling.
  * This API is provided due to the lack of such API in Pekko HTTP.
  * If and when Pekko HTTP provides a Java manual marshalling and
  * unmarshalling API, this class may become deprecated.
  *
  * @param ec An execution context
  * @param mat A materializer
  */
class MarshalUnmarshal(implicit ec: ExecutionContext, mat: Materializer) {

  /**
    * Java API for manual unmarshalling.
    * @param unmarshaller The unmarshaller to use
    * @param from The object to unmarshal
    * @tparam T The type to unmarshal from
    * @tparam U The type to unmarshal to
    * @return CompletionStage of the unmarshal result
    */
  def apply[T, U](unmarshaller: Unmarshaller[T, U], from: T): CompletionStage[U] = {
    implicit val su = unmarshaller.asScala
    Unmarshal(from).to[U].toJava
  }

  /**
    * Java API for manual marshalling.
    * @param marshaller The marshaller to use
    * @param from The object to marshal
    * @tparam T The type to marshal from
    * @tparam U The type to marshal to
    * @return CompletionStage of the marshal result
    */
  def apply[T, U](marshaller: Marshaller[T, U], from: T): CompletionStage[U] = {
    implicit val m = marshaller.asScala
    Marshal(from).to[U].toJava
  }
}

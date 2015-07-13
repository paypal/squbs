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
package org.squbs.httpclient.japi

import java.util.Optional

import akka.actor.ActorSystem
import akka.util.Timeout
import org.squbs.httpclient._
import org.squbs.httpclient.env.{Default, Environment}
import org.squbs.httpclient.json.JsonProtocol
import org.squbs.proxy.SimplePipelineConfig
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._

/**
 * Created by lma on 6/11/2015.
 */
class HttpClient(private val delegate: org.squbs.httpclient.HttpClient) {

  import JsonProtocol.optionToMarshaller
  import JsonProtocol.ClassSupport.classToFromResponseUnmarshaller

  import scala.compat.java8.OptionConverters._

  def name = delegate.name

  def withConfig(config: Configuration): HttpClient = {
    new HttpClient(delegate.withConfig(config))
  }

  def withSettings(settings: Settings): HttpClient = {
    new HttpClient(delegate.withSettings(settings))
  }

  def withPipeline(pipeline: Optional[SimplePipelineConfig]): HttpClient = {
    new HttpClient(delegate.withPipeline(pipeline.asScala))
  }

  def markDown = delegate.markDown

  def markUp = delegate.markUp

  def readyFuture = delegate.readyFuture

  def endpoint = delegate.endpoint


  def get[R](uri: String, clazz: Class[R]) = delegate.get(uri)(unmarshaller = clazz)

  def get[R](uri: String, reqSettings: RequestSettings, clazz: Class[R]) = delegate.get(uri, reqSettings)(unmarshaller = clazz)

  def get[R](uri: String, timeout: Timeout, clazz: Class[R]) = delegate.get(uri)(timeout, clazz)

  def get[R](uri: String, reqSettings: RequestSettings, timeout: Timeout, clazz: Class[R]) = delegate.get(uri, reqSettings)(timeout, clazz)

  def get[R](uri: String, unmarshaller: FromResponseUnmarshaller[R]) = delegate.get(uri)(unmarshaller = unmarshaller)

  def get[R](uri: String, reqSettings: RequestSettings, unmarshaller: FromResponseUnmarshaller[R]) = delegate.get(uri, reqSettings)(unmarshaller = unmarshaller)

  def get[R](uri: String, timeout: Timeout, unmarshaller: FromResponseUnmarshaller[R]) = delegate.get(uri)(timeout, unmarshaller)

  def get[R](uri: String, reqSettings: RequestSettings, timeout: Timeout, unmarshaller: FromResponseUnmarshaller[R]) = delegate.get(uri, reqSettings)(timeout, unmarshaller)


  def options[R](uri: String, clazz: Class[R]) = delegate.options(uri)(unmarshaller = clazz)

  def options[R](uri: String, reqSettings: RequestSettings, clazz: Class[R]) = delegate.options(uri, reqSettings)(unmarshaller = clazz)

  def options[R](uri: String, timeout: Timeout, clazz: Class[R]) = delegate.options(uri)(timeout, clazz)

  def options[R](uri: String, reqSettings: RequestSettings, timeout: Timeout, clazz: Class[R]) = delegate.options(uri, reqSettings)(timeout, clazz)

  def options[R](uri: String, unmarshaller: FromResponseUnmarshaller[R]) = delegate.options(uri)(unmarshaller = unmarshaller)

  def options[R](uri: String, reqSettings: RequestSettings, unmarshaller: FromResponseUnmarshaller[R]) = delegate.options(uri, reqSettings)(unmarshaller = unmarshaller)

  def options[R](uri: String, timeout: Timeout, unmarshaller: FromResponseUnmarshaller[R]) = delegate.options(uri)(timeout, unmarshaller)

  def options[R](uri: String, reqSettings: RequestSettings, timeout: Timeout, unmarshaller: FromResponseUnmarshaller[R]) = delegate.options(uri, reqSettings)(timeout, unmarshaller)


  def delete[R](uri: String, clazz: Class[R]) = delegate.delete(uri)(unmarshaller = clazz)

  def delete[R](uri: String, reqSettings: RequestSettings, clazz: Class[R]) = delegate.delete(uri, reqSettings)(unmarshaller = clazz)

  def delete[R](uri: String, timeout: Timeout, clazz: Class[R]) = delegate.delete(uri)(timeout, clazz)

  def delete[R](uri: String, reqSettings: RequestSettings, timeout: Timeout, clazz: Class[R]) = delegate.delete(uri, reqSettings)(timeout, clazz)

  def delete[R](uri: String, unmarshaller: FromResponseUnmarshaller[R]) = delegate.delete(uri)(unmarshaller = unmarshaller)

  def delete[R](uri: String, reqSettings: RequestSettings, unmarshaller: FromResponseUnmarshaller[R]) = delegate.delete(uri, reqSettings)(unmarshaller = unmarshaller)

  def delete[R](uri: String, timeout: Timeout, unmarshaller: FromResponseUnmarshaller[R]) = delegate.delete(uri)(timeout, unmarshaller)

  def delete[R](uri: String, reqSettings: RequestSettings, timeout: Timeout, unmarshaller: FromResponseUnmarshaller[R]) = delegate.delete(uri, reqSettings)(timeout, unmarshaller)


  def post[T <: AnyRef, R](uri: String, content: Optional[T], clazz: Class[R]) = delegate.post(uri, content.asScala)(marshaller = content.asScala, unmarshaller = clazz)

  def post[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, clazz: Class[R]) = delegate.post(uri, content.asScala, reqSettings)(marshaller = content.asScala, unmarshaller = clazz)

  def post[T <: AnyRef, R](uri: String, content: Optional[T], timeout: Timeout, clazz: Class[R]) = delegate.post(uri, content.asScala)(timeout, marshaller = content.asScala, unmarshaller = clazz)

  def post[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, timeout: Timeout, clazz: Class[R]) = delegate.post(uri, content.asScala, reqSettings)(timeout, content.asScala, clazz)

  def post[T <: AnyRef, R](uri: String, content: Optional[T], marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content.asScala)(marshaller = marshaller, unmarshaller = unmarshaller)

  def post[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content.asScala, reqSettings)(marshaller = marshaller, unmarshaller = unmarshaller)

  def post[T <: AnyRef, R](uri: String, content: Optional[T], timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content.asScala)(timeout, marshaller, unmarshaller)

  def post[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content.asScala, reqSettings)(timeout, marshaller, unmarshaller)


  def put[T <: AnyRef, R](uri: String, content: Optional[T], clazz: Class[R]) = delegate.put(uri, content.asScala)(marshaller = content.asScala, unmarshaller = clazz)

  def put[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, clazz: Class[R]) = delegate.put(uri, content.asScala, reqSettings)(marshaller = content.asScala, unmarshaller = clazz)

  def put[T <: AnyRef, R](uri: String, content: Optional[T], timeout: Timeout, clazz: Class[R]) = delegate.put(uri, content.asScala)(timeout, marshaller = content.asScala, unmarshaller = clazz)

  def put[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, timeout: Timeout, clazz: Class[R]) = delegate.put(uri, content.asScala, reqSettings)(timeout, content.asScala, clazz)

  def put[T <: AnyRef, R](uri: String, content: Optional[T], marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content.asScala)(marshaller = marshaller, unmarshaller = unmarshaller)

  def put[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content.asScala, reqSettings)(marshaller = marshaller, unmarshaller = unmarshaller)

  def put[T <: AnyRef, R](uri: String, content: Optional[T], timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content.asScala)(timeout, marshaller, unmarshaller)

  def put[T <: AnyRef, R](uri: String, content: Optional[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content.asScala, reqSettings)(timeout, marshaller, unmarshaller)


  def raw = new RawHttpClient(delegate.raw)

  class RawHttpClient(rawDelegate: org.squbs.httpclient.RawHttpClient) {

    def get(uri: String) = rawDelegate.get(uri)

    def get(uri: String, reqSettings: RequestSettings) = rawDelegate.get(uri, reqSettings)

    def get(uri: String, timeout: Timeout) = rawDelegate.get(uri)(timeout)

    def get(uri: String, reqSettings: RequestSettings, timeout: Timeout) = rawDelegate.get(uri, reqSettings)(timeout)

    def post[T <: AnyRef](uri: String, content: Optional[T]) = rawDelegate.post(uri, content.asScala)(marshaller = content.asScala)

    def post[T <: AnyRef](uri: String, content: Optional[T], reqSettings: RequestSettings) = rawDelegate.post(uri, content.asScala, reqSettings)(marshaller = content.asScala)

    def post[T <: AnyRef](uri: String, content: Optional[T], timeout: Timeout) = rawDelegate.post(uri, content.asScala)(timeout, content.asScala)

    def post[T <: AnyRef](uri: String, content: Optional[T], reqSettings: RequestSettings, marshaller: Marshaller[T]) = rawDelegate.post(uri, content.asScala, reqSettings)(marshaller = marshaller)

    def post[T <: AnyRef](uri: String, content: Optional[T], timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.post(uri, content.asScala)(timeout, marshaller)

    def post[T <: AnyRef](uri: String, content: Optional[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.post(uri, content.asScala, reqSettings)(timeout, marshaller)

    def put[T <: AnyRef](uri: String, content: Optional[T]) = rawDelegate.put(uri, content.asScala)(marshaller = content.asScala)

    def put[T <: AnyRef](uri: String, content: Optional[T], reqSettings: RequestSettings) = rawDelegate.put(uri, content.asScala, reqSettings)(marshaller = content.asScala)

    def put[T <: AnyRef](uri: String, content: Optional[T], timeout: Timeout) = rawDelegate.put(uri, content.asScala)(timeout, content.asScala)

    def put[T <: AnyRef](uri: String, content: Optional[T], reqSettings: RequestSettings, marshaller: Marshaller[T]) = rawDelegate.put(uri, content.asScala, reqSettings)(marshaller = marshaller)

    def put[T <: AnyRef](uri: String, content: Optional[T], timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.put(uri, content.asScala)(timeout, marshaller)

    def put[T <: AnyRef](uri: String, content: Optional[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.put(uri, content.asScala, reqSettings)(timeout, marshaller)

    def head(uri: String) = rawDelegate.head(uri)

    def head(uri: String, reqSettings: RequestSettings) = rawDelegate.head(uri, reqSettings)

    def head(uri: String, timeout: Timeout) = rawDelegate.head(uri)(timeout)

    def head(uri: String, reqSettings: RequestSettings, timeout: Timeout) = rawDelegate.head(uri, reqSettings)(timeout)

    def delete(uri: String) = rawDelegate.delete(uri)

    def delete(uri: String, reqSettings: RequestSettings) = rawDelegate.delete(uri, reqSettings)

    def delete(uri: String, timeout: Timeout) = rawDelegate.delete(uri)(timeout)

    def delete(uri: String, reqSettings: RequestSettings, timeout: Timeout) = rawDelegate.delete(uri, reqSettings)(timeout)

    def options(uri: String) = rawDelegate.options(uri)

    def options(uri: String, reqSettings: RequestSettings) = rawDelegate.options(uri, reqSettings)

    def options(uri: String, timeout: Timeout) = rawDelegate.options(uri)(timeout)

    def options(uri: String, reqSettings: RequestSettings, timeout: Timeout) = rawDelegate.options(uri, reqSettings)(timeout)

  }

}

object HttpClientFactory {

  def get(name: String)(implicit system: ActorSystem): HttpClient = get(name, Default)(system)

  def get(name: String, env: Environment)(implicit system: ActorSystem): HttpClient = {
    new HttpClient(org.squbs.httpclient.HttpClientFactory.get(name, env)(system))
  }


}

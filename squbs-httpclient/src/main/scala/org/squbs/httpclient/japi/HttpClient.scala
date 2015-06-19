package org.squbs.httpclient.japi

import akka.actor.ActorSystem
import akka.util.Timeout
import org.squbs.httpclient._
import org.squbs.httpclient.env.{Default, Environment}
import org.squbs.proxy.SimplePipelineConfig
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._

/**
 * Created by lma on 6/11/2015.
 */
class HttpClient(private val delegate: org.squbs.httpclient.HttpClient) {

  import org.squbs.httpclient.json.Json4jJacksonProtocol.classToFromResponseUnmarshaller
  import org.squbs.httpclient.json.Json4jJacksonProtocol.optionToMarshaller

  def name = delegate.name

  def withConfig(config: Configuration): HttpClient = {
    new HttpClient(delegate.withConfig(config))
  }

  def withSettings(settings: Settings): HttpClient = {
    new HttpClient(delegate.withSettings(settings))
  }

  def withPipeline(pipeline: Option[SimplePipelineConfig]): HttpClient = {
    new HttpClient(delegate.withPipeline(pipeline))
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


  def post[T <: AnyRef, R](uri: String, content: Option[T], clazz: Class[R]) = delegate.post(uri, content)(marshaller = content, unmarshaller = clazz)

  def post[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, clazz: Class[R]) = delegate.post(uri, content, reqSettings)(marshaller = content, unmarshaller = clazz)

  def post[T <: AnyRef, R](uri: String, content: Option[T], timeout: Timeout, clazz: Class[R]) = delegate.post(uri, content)(timeout, marshaller = content, unmarshaller = clazz)

  def post[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, timeout: Timeout, clazz: Class[R]) = delegate.post(uri, content, reqSettings)(timeout, content, clazz)

  def post[T <: AnyRef, R](uri: String, content: Option[T], marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content)(marshaller = marshaller, unmarshaller = unmarshaller)

  def post[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content, reqSettings)(marshaller = marshaller, unmarshaller = unmarshaller)

  def post[T <: AnyRef, R](uri: String, content: Option[T], timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content)(timeout, marshaller, unmarshaller)

  def post[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.post(uri, content, reqSettings)(timeout, marshaller, unmarshaller)


  def put[T <: AnyRef, R](uri: String, content: Option[T], clazz: Class[R]) = delegate.put(uri, content)(marshaller = content, unmarshaller = clazz)

  def put[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, clazz: Class[R]) = delegate.put(uri, content, reqSettings)(marshaller = content, unmarshaller = clazz)

  def put[T <: AnyRef, R](uri: String, content: Option[T], timeout: Timeout, clazz: Class[R]) = delegate.put(uri, content)(timeout, marshaller = content, unmarshaller = clazz)

  def put[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, timeout: Timeout, clazz: Class[R]) = delegate.put(uri, content, reqSettings)(timeout, content, clazz)

  def put[T <: AnyRef, R](uri: String, content: Option[T], marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content)(marshaller = marshaller, unmarshaller = unmarshaller)

  def put[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content, reqSettings)(marshaller = marshaller, unmarshaller = unmarshaller)

  def put[T <: AnyRef, R](uri: String, content: Option[T], timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content)(timeout, marshaller, unmarshaller)

  def put[T <: AnyRef, R](uri: String, content: Option[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T], unmarshaller: FromResponseUnmarshaller[R]) = delegate.put(uri, content, reqSettings)(timeout, marshaller, unmarshaller)


  def raw = new RawHttpClient(delegate.raw)

  class RawHttpClient(rawDelegate: org.squbs.httpclient.RawHttpClient) {

    def get(uri: String) = rawDelegate.get(uri)

    def get(uri: String, reqSettings: RequestSettings) = rawDelegate.get(uri, reqSettings)

    def get(uri: String, timeout: Timeout) = rawDelegate.get(uri)(timeout)

    def get(uri: String, reqSettings: RequestSettings, timeout: Timeout) = rawDelegate.get(uri, reqSettings)(timeout)

    def post[T <: AnyRef](uri: String, content: Option[T]) = rawDelegate.post(uri, content)(marshaller = content)

    def post[T <: AnyRef](uri: String, content: Option[T], reqSettings: RequestSettings) = rawDelegate.post(uri, content, reqSettings)(marshaller = content)

    def post[T <: AnyRef](uri: String, content: Option[T], timeout: Timeout) = rawDelegate.post(uri, content)(timeout, content)

    def post[T <: AnyRef](uri: String, content: Option[T], reqSettings: RequestSettings, marshaller: Marshaller[T]) = rawDelegate.post(uri, content, reqSettings)(marshaller = marshaller)

    def post[T <: AnyRef](uri: String, content: Option[T], timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.post(uri, content)(timeout, marshaller)

    def post[T <: AnyRef](uri: String, content: Option[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.post(uri, content, reqSettings)(timeout, marshaller)

    def put[T <: AnyRef](uri: String, content: Option[T]) = rawDelegate.put(uri, content)(marshaller = content)

    def put[T <: AnyRef](uri: String, content: Option[T], reqSettings: RequestSettings) = rawDelegate.put(uri, content, reqSettings)(marshaller = content)

    def put[T <: AnyRef](uri: String, content: Option[T], timeout: Timeout) = rawDelegate.put(uri, content)(timeout, content)

    def put[T <: AnyRef](uri: String, content: Option[T], reqSettings: RequestSettings, marshaller: Marshaller[T]) = rawDelegate.put(uri, content, reqSettings)(marshaller = marshaller)

    def put[T <: AnyRef](uri: String, content: Option[T], timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.put(uri, content)(timeout, marshaller)

    def put[T <: AnyRef](uri: String, content: Option[T], reqSettings: RequestSettings, timeout: Timeout, marshaller: Marshaller[T]) = rawDelegate.put(uri, content, reqSettings)(timeout, marshaller)

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

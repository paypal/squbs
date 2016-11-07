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
package org.squbs.httpclient.japi;

import akka.actor.ActorSystem;
import org.squbs.httpclient.RequestSettings;
import scala.concurrent.Future;
import spray.http.HttpResponse;
import spray.httpx.marshalling.Marshaller;
import spray.httpx.unmarshalling.Deserializer;

import java.util.Optional;

public class HttpClientJ {

    public static Future<HttpResponse> rawGet(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().get(uri);
    }

    public static Future<HttpResponse> rawGet(String clientName, ActorSystem system, String uri,
                                              RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().get(uri, reqSetting);
    }

    public static <T> Future<HttpResponse> rawPost(String clientName, ActorSystem system, String uri,
                                                   Optional<T> data) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().post(uri, data);
    }

    public static <T> Future<HttpResponse>
    rawPost(String clientName, ActorSystem system, String uri, Optional<T> data, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().post(uri, data, reqSetting);
    }

    public static <T> Future<HttpResponse> rawPost(String clientName, ActorSystem system, String uri, Optional<T> data,
                                                   Marshaller<T> marshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().post(uri, data, marshaller);
    }

    public static <T> Future<HttpResponse> rawPost(String clientName, ActorSystem system, String uri, Optional<T> data,
                                                   RequestSettings reqSetting, Marshaller<T> marshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().post(uri, data, reqSetting, marshaller);
    }

    public static <T> Future<HttpResponse> rawPut(String clientName, ActorSystem system, String uri, Optional<T> data) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().put(uri, data);
    }

    public static <T> Future<HttpResponse> rawPut(String clientName, ActorSystem system, String uri, Optional<T> data,
                                                  RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().put(uri, data, reqSetting);
    }

    public static <T> Future<HttpResponse> rawPut(String clientName, ActorSystem system, String uri, Optional<T> data,
                                                  Marshaller<T> marshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().put(uri, data, marshaller);
    }

    public static <T> Future<HttpResponse> rawPut(String clientName, ActorSystem system, String uri, Optional<T> data,
                                                  RequestSettings reqSetting, Marshaller<T> marshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().put(uri, data, reqSetting, marshaller);
    }

    public static <T> Future<T> get(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.get(uri, type);
    }

    public static <T> Future<T> get(String clientName, ActorSystem system, String uri, Class<T> type,
                                    RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.get(uri, reqSetting, type);
    }

    public static <T> Future<T> get(String clientName, ActorSystem system, String uri,
                                    Deserializer<HttpResponse, T> unmarshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.get(uri, unmarshaller);
    }

    public static <T> Future<T> get(String clientName, ActorSystem system, String uri,
                                    Deserializer<HttpResponse, T> unmarshaller, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.get(uri, reqSetting, unmarshaller);
    }

    public static Future<HttpResponse> rawHead(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().head(uri);
    }

    public static Future<HttpResponse> rawHead(String clientName, ActorSystem system, String uri,
                                               RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().head(uri, reqSetting);
    }

    public static Future<HttpResponse> rawOptions(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().options(uri);
    }

    public static Future<HttpResponse> rawOptions(String clientName, ActorSystem system, String uri,
                                                  RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().options(uri, reqSetting);
    }

    public static <T> Future<T> options(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.options(uri, type);
    }

    public static <T> Future<T> options(String clientName, ActorSystem system, String uri, Class<T> type,
                                        RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.options(uri, reqSetting, type);
    }

    public static <T> Future<T> options(String clientName, ActorSystem system, String uri,
                                        Deserializer<HttpResponse, T> unmarshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.options(uri, unmarshaller);
    }

    public static <T> Future<T> options(String clientName, ActorSystem system, String uri,
                                        Deserializer<HttpResponse, T> unmarshaller, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.options(uri, reqSetting, unmarshaller);
    }

    public static Future<HttpResponse> rawDelete(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().delete(uri);
    }

    public static Future<HttpResponse> rawDelete(String clientName, ActorSystem system, String uri,
                                                 RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().delete(uri, reqSetting);
    }

    public static <T> Future<T> delete(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.delete(uri, type);
    }

    public static <T> Future<T> delete(String clientName, ActorSystem system, String uri, Class<T> type,
                                       RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.delete(uri, reqSetting, type);
    }

    public static <T> Future<T> delete(String clientName, ActorSystem system, String uri,
                                       Deserializer<HttpResponse, T> unmarshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.delete(uri, unmarshaller);
    }

    public static <T> Future<T> delete(String clientName, ActorSystem system, String uri,
                                       Deserializer<HttpResponse, T> unmarshaller, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.delete(uri, reqSetting, unmarshaller);
    }

    public static <T, R> Future<R> post(String clientName, ActorSystem system, String uri, Optional<T> data,
                                        Class<R> respType) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.post(uri, data, respType);
    }

    public static <T, R> Future<R> post(String clientName, ActorSystem system, String uri, Optional<T> data,
                                        Class<R> respType, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.post(uri, data, reqSetting, respType);
    }

    public static <T, R> Future<R> post(String clientName, ActorSystem system, String uri, Optional<T> data,
                                        Marshaller<T> marshaller, Deserializer<HttpResponse, R> unmarshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.post(uri, data, marshaller, unmarshaller);
    }

    public static <T, R> Future<R> post(String clientName, ActorSystem system, String uri, Optional<T> data,
                                        Marshaller<T> marshaller, Deserializer<HttpResponse, R> unmarshaller,
                                        RequestSettings reqSettings) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.post(uri, data, reqSettings, marshaller, unmarshaller);
    }

    public static <T, R> Future<R> put(String clientName, ActorSystem system, String uri, Optional<T> data,
                                       Class<R> respType) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.put(uri, data, respType);
    }

    public static <T, R> Future<R> put(String clientName, ActorSystem system, String uri, Optional<T> data,
                                       Class<R> respType, RequestSettings reqSettings) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.put(uri, data, reqSettings, respType);
    }

    public static <T, R> Future<R> put(String clientName, ActorSystem system, String uri, Optional<T> data,
                                       Marshaller<T> marshaller, Deserializer<HttpResponse, R> unmarshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.put(uri, data, marshaller, unmarshaller);
    }

    public static <T, R> Future<R> put(String clientName, ActorSystem system, String uri, Optional<T> data,
                                       Marshaller<T> marshaller, Deserializer<HttpResponse, R> unmarshaller,
                                       RequestSettings reqSettings) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.put(uri, data, reqSettings, marshaller, unmarshaller);
    }
}

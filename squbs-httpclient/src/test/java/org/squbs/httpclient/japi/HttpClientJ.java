package org.squbs.httpclient.japi;

import akka.actor.ActorSystem;
import org.squbs.httpclient.japi.HttpClientFactory;
import org.squbs.httpclient.RequestSettings;
import scala.Option;
import scala.concurrent.Future;
import spray.http.HttpResponse;
import spray.httpx.marshalling.Marshaller;
import spray.httpx.unmarshalling.Deserializer;

/**
 * Created by lma on 6/5/2015.
 */
public class HttpClientJ {

    public static Future<HttpResponse> rawGet(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().get(uri);
    }

    public static Future<HttpResponse> rawGet(String clientName, ActorSystem system, String uri, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().get(uri, reqSetting);
    }

    public static <T> Future<HttpResponse> rawPost(String clientName, ActorSystem system, String uri, Option<T> data, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().post(uri, data, reqSetting);

    }

    public static <T> Future<HttpResponse> rawPost(String clientName, ActorSystem system, String uri, Option<T> data) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().post(uri, data);

    }

    public static <T> Future<HttpResponse> rawPut(String clientName, ActorSystem system, String uri, Option<T> data) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().put(uri, data);

    }

    public static <T> Future<T> get(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.get(uri, type);

    }

    public static Future<HttpResponse> rawHead(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().head(uri);
    }

    public static Future<HttpResponse> rawOptions(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().options(uri);
    }

    public static <T> Future<T> options(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.options(uri, type);

    }

    public static Future<HttpResponse> rawDelete(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().delete(uri);
    }

    public static <T> Future<T> delete(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.delete(uri, type);

    }

    public static <T,R> Future<R> post(String clientName, ActorSystem system, String uri, Option<T> data, Class<R> respType) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.post(uri, data, respType);

    }


    public static <T,R> Future<R> post(String clientName, ActorSystem system, String uri, Option<T> data, Marshaller<T> marshaller, Deserializer<HttpResponse, R> unmarshaller) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.post(uri, data, marshaller, unmarshaller);
    }


    public static <T,R> Future<R> put(String clientName, ActorSystem system, String uri, Option<T> data, Class<R> respType) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.put(uri, data, respType);

    }


}

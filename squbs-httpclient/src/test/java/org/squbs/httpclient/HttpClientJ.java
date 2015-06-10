package org.squbs.httpclient;

import akka.actor.ActorSystem;
import scala.Option;
import scala.concurrent.Future;
import spray.http.HttpResponse;

/**
 * Created by lma on 6/5/2015.
 */
public class HttpClientJ {

    public static Future<HttpResponse> rawGet(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().get(uri);
    }

    public static Future<HttpResponse> rawGet(String clientName, ActorSystem system, String uri, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().get(uri, reqSetting);
    }

    public static <T> Future<HttpResponse> rawPost(String clientName, ActorSystem system, String uri, Option<T> data, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().post(uri, data, reqSetting);

    }

    public static <T> Future<HttpResponse> rawPost(String clientName, ActorSystem system, String uri, Option<T> data) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().post(uri, data);

    }

    public static <T> Future<HttpResponse> rawPut(String clientName, ActorSystem system, String uri, Option<T> data) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().put(uri, data);

    }

    public static <T> Future<T> get(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.toJava().get(uri, type);

    }

    public static Future<HttpResponse> rawHead(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().head(uri);
    }

    public static Future<HttpResponse> rawOptions(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().options(uri);
    }

    public static <T> Future<T> options(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.toJava().options(uri, type);

    }

    public static Future<HttpResponse> rawDelete(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().delete(uri);
    }

    public static <T> Future<T> delete(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.toJava().delete(uri, type);

    }

    public static <T,R> Future<R> post(String clientName, ActorSystem system, String uri, Option<T> data, Class<R> respType) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.toJava().post(uri, data, respType);

    }

    public static <T,R> Future<R> put(String clientName, ActorSystem system, String uri, Option<T> data, Class<R> respType) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.toJava().put(uri, data, respType);

    }


}

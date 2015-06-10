package org.squbs.httpclient;

import akka.actor.ActorSystem;
import scala.Option;
import scala.concurrent.Future;
import spray.http.HttpResponse;

/**
 * Created by lma on 6/5/2015.
 */
public class HttpClientJ {

    public static Future<HttpResponse> getRaw(String clientName, ActorSystem system, String uri) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().get(uri);
    }

    public static Future<HttpResponse> getRaw(String clientName, ActorSystem system, String uri, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().get(uri, reqSetting);
    }

    public static <T> Future<HttpResponse> postRaw(String clientName, ActorSystem system, String uri, Option<T> data, RequestSettings reqSetting) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.raw().toJava().post(uri, data, reqSetting);

    }

    public static <T> Future<T> get(String clientName, ActorSystem system, String uri, Class<T> type) {
        HttpClient client = HttpClientFactory.get(clientName, system);
        return client.toJava().get(uri, type);

    }


}

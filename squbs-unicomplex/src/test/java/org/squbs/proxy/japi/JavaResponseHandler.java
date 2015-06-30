package org.squbs.proxy.japi;

import akka.actor.ActorContext;
import akka.dispatch.Futures;
import org.squbs.pipeline.Handler;
import org.squbs.pipeline.RequestContext;
import scala.Tuple2;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import spray.http.HttpHeaders;

import java.util.Arrays;
import java.util.Date;

/**
 * Created by lma on 6/26/2015.
 */
public class JavaResponseHandler implements Handler {
    @Override
    public Future<RequestContext> process(RequestContext reqCtx, ExecutionContext executor, ActorContext context) {
        return Futures.successful(reqCtx
                .addResponseHeader(new HttpHeaders.RawHeader("JavaResponseHandler", "JavaResponseHandler"))
                .withAttributes(Arrays.asList(new Tuple2("someAttr", Arrays.asList("AttrValue")))));
        //return Future$.MODULE$.successful(reqCtx.addRequestHeader(new HttpHeaders.RawHeader("JavaRequestHandler", "JavaRequestHandler")));
    }
}

package org.squbs.proxy.japi;

import akka.actor.ActorContext;
import akka.dispatch.Futures;
import org.squbs.pipeline.Handler;
import org.squbs.pipeline.RequestContext;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import spray.http.HttpHeaders;

/**
 * Created by lma on 6/26/2015.
 */
public class JavaRequestHandler implements Handler {
    @Override
    public Future<RequestContext> process(RequestContext reqCtx, ExecutionContext executor, ActorContext context) {
        return Futures.successful(reqCtx.addRequestHeader(new HttpHeaders.RawHeader("JavaRequestHandler", "JavaRequestHandler")));
        //return Future$.MODULE$.successful(reqCtx.addRequestHeader(new HttpHeaders.RawHeader("JavaRequestHandler", "JavaRequestHandler")));
    }
}

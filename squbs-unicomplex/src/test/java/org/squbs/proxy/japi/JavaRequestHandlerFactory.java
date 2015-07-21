package org.squbs.proxy.japi;

import akka.actor.ActorRefFactory;
import com.typesafe.config.Config;
import org.squbs.pipeline.Handler;
import org.squbs.pipeline.HandlerFactory;
import scala.Option;

/**
 * Created by lma on 6/26/2015.
 */
public class JavaRequestHandlerFactory implements HandlerFactory {
    @Override
    public Option<Handler> create(Option<Config> config, ActorRefFactory actorRefFactory) {
        return Option.apply(new JavaRequestHandler());
    }
}

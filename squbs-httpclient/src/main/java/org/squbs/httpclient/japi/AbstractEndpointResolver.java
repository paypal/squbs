package org.squbs.httpclient.japi;

import org.squbs.httpclient.endpoint.EndpointResolver;
import org.squbs.httpclient.env.Default;
import org.squbs.httpclient.env.Environment;

/**
 * Created by lma on 7/15/2015.
 */
public abstract class AbstractEndpointResolver implements EndpointResolver {

    @Override
    public Environment resolve$default$2() {
        return Default.value();
    }

}

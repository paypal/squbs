#Proxy Of Squbs Service

### Overview
Sometimes, we may have common biz logic accross different squbs-services.
For example: CAL, Authentication/Authorization, tracking, cookie management, A/B testing, etc.

Usually we don't what the service owner to take care of these common stuffs in their Actor/Router.
That's why we introduce the squbs proxy to facilitate it.

Generally speaking, a squbs proxy is an actor which acting like a bridge in between the responder and the squbs service.
That is to say:
* All messages sent from resonder to squbs service will go thru the proxy actor
* Vice versa, all messages sent from squbs service to responder will go thru the proxy actor.


Below section will describe how to enable a proxy for squbs service.

### Proxy declaration

In your squbs-meta.conf, you can specify proxy for your service:

```json

squbs-services = [
  {
    class-name = com.ebay.myapp.MyActor
    web-context = mypath
    proxy-name = myProxy
  }
]

```



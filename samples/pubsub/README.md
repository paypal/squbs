Publish-Subscribe Sample
========================

The publish-subscribe sample reflects real needs for push-broadcasting events over the web. Very commonly, a web page served by a different application would open an SSE channel to this server to listen for events. Applications will make a rest call to this server to publish events that will be listened to by all the browsers. The design center of this application is around small events (few bytes to about 1 kilobyte in size) that are pushed out at very low latency. It is not designed for transfer of large messages.

**Concepts**:

* *Channel* is an event stream clients can subscribe and listen to.
* *Category* is a group of channels that publish similar events. A channel belongs to a category.
* *Event* is a single event to be published and listened to by browsers.

**Services**:

Services are grouped into admin services, publish services, and subscriptions. They have the URL structure as follows:

**Admin services**:

* **/adm/new/&lt;catid&gt;** - The category id could be any lower case string conforming to URL path specifications.
It should be encoded as necessary. This is a POST request for creating a category passing category properties as JSON
content. The content of the POST is a JSON
string having a category description, category metadata, and default channel configuration. After a category is created,
 a channel can be added to the category.

* **/adm/new/&lt;catid&gt;/&lt;channelid&gt;** - The category id must exist or this call would return a 404 (not found).
The channel id, similar to a category id could be any URL path conformant string. This is a POST request for creating
a channel passing channel properties as JSON content. After a channel is created, it can be subscribed to.

* **/adm/stop/&lt;catid&gt;/&lt;channelid&gt;** - Stops and removes the respective channel. All connections listening on
the channel will be closed as a result. This is a POST request for stopping a channel. Channel properties need to be
passed as JSON content. If the channel is secured, a channel-key needs to be passed with the correct key for this
channel.

* **/adm/delete/&lt;catid&gt;** - Removes the respective category. This call will fail with a 409 (Conflict) if not all
channels have been removed. This is a POST request. Category properties need to be passed as JSON content. If the
category is secured, a category-key needs to be passed with correct key for this category.

* **/adm/categories** - List all categories by id, providing hyperlinks to the category information. This is a GET request.
The response is JSON listing all categories.

* **/adm/channels/&lt;catid&gt;** - Lists all channels in the given category. This is a GET request. The response is JSON
listing all channels.

* **/adm/info** - Provides basic system info and number of categories. This is a GET request. The response is JSON
outlining system stats and the number of categories.

* **/adm/info/&lt;catid&gt;** - Provides the category metadata, configuration, and stats. This is a GET request. The
response is JSON providing category metadata and configuration. Also it provides the number of channels. No security
key information is exposed.

* **/adm/info/&lt;catid&gt;/&lt;channelid&gt;** - Provides the metadata, configuration, and stats for the channel.
This is a GET request. The response is JSON providing channel metadta and configuration. Also it provides the current
number of subscribers on the channel. No security key information is exposed.

**Publish service**:

* **/pub/&lt;catid&gt;/&lt;channelid&gt;/&lt;eventid&gt;** - POST/PUT request. The payload is usually JSON but does not
need to be. The payload will be published to subscribers as is as an SSE event with the event as published.
The payload is not parsed and newlines are not inserted. Make sure the content does not have newlines. If the channel
is secured, the "channel-key" header must be passed with the correct key or the publish will be denied.

**Subscribe service**:

* **/evt/&lt;catid&gt;/&lt;channelid&gt;** - Opening this connection with accept: text/event-stream as is done
 automatically by the JavaScript (and other) EventSource API will start listening to the events. The stream follows
 the [SSE working draft](http://www.w3.org/TR/2009/WD-eventsource-20091029/)

Properties
----------
All POST administrative requests need to pass a property as JSON. This contains a key-value pair for each property.
Properties may include:

* Free form metadata (such as description, owner). The system stores this metadata and provides it using the info
admin calls. No further processing is done on this metadata.

* Security properties including category-key and channel-key.

* Configuration.

**Category Properties**:

Category properties can contain any number of JSON fields used in free form, channel properties, and **category-key**
for a secured category. All properties are passed to channels created on this category allowing category properties to
contain channel configuration and metadata.

**Channel Properties**:

Channel properties can contain any number of JSON fields used in free form and queried by the info service call. For a
secured channel, the **channel-key** property should be provided. If the channel is created on a secured category, the
channel-key defaults to the category-key if not provided. The following provides a list and behavior of channel
configuration properties:

* **last-event-on-connect** - Boolean (true/false) property stating whether the last event should be sent to a subscriber
 on a new connect. Default is true.

* **event-cache-size** - Integer. The number of past events to keep. These cached events is send on client reconnect. A slow
reconnect may have missed multiple events. The client receives these events on reconnect if they are still in cache.
the default event-cache-size is 1.

* **max-time-to-reconnect** - The largest amount of time a client can stay connected. Default is 120 seconds. After
this time, the client is automatically disconnected. Proper SSE clients will promptly reconnect and fetch the missed
events, if any.

* **max-subscriber-queue-size** - Network feedback mechanism for subscriber for extremely high message rate channels and/or
extremely slow clients overflowing the network buffer. The number of events provided here will be queued. If the
queue size is reached, the client is disconnected and may connect again on a faster channel using SSE mechanisms
to fetch missed events if they are still in cache.

Security
--------

Simple security is provided via the **category-key** and **channel-key** properties. Secure categories would pass a
**category-key** property on creation. This key is then used for creating channels and removing the category.

Secure channels would pass a **channel-key** property on creation. If no **channel-key** is passed and the channel is
part of a secured category, they parent category's **category-key** is used as the channel-key. The **channel-key** is
needed for publishing to the channel and stopping the channel.

Keys are passed as part of the request property. One exception is on publish, where **channel-key** is passed as a
request header of the same name as to keep the content unpolluted.
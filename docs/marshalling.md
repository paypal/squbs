# Marshalling and Unmarshalling

### Overview

Marshalling and unmarshalling is used both on the client and server side. On the server side it is used to map an incoming request to a Scala or Java object and to map a Scala or Java object to an outgoing response. Similarly, on the client side, it is used to marshal an object to an outgoing HTTP request and unmarshal it from an incoming response. There could be a multitude of content formats for marshalling/unmarshalling, common ones are JSON and XML. Please see the following pages for quick examples of JSON marshalling and unmarshalling in pekko HTTP:

* Scala - [spray-json Support](http://doc.pekko.io/docs/pekko-http/current/scala/http/common/json-support.html#spray-json-support)
* Java - [Jackson Support](http://doc.pekko.io/docs/pekko-http/current/java/http/common/json-support.html#json-support-via-jackson).

pekko HTTP provides marshalling/unmarshalling facilities explained in [Scala marshalling](http://doc.pekko.io/docs/pekko-http/current/scala/http/common/marshalling.html)/[unmarshalling](http://doc.pekko.io/docs/pekko-http/current/scala/http/common/unmarshalling.html) and [Java marshalling](http://doc.pekko.io/docs/pekko-http/current/java/http/common/marshalling.html)/[unmarshalling](http://doc.pekko.io/docs/pekko-http/current/java/http/common/unmarshalling.html). Also, there are other open source marshallers and unmarshallers for pekko HTTP available for different formats and using different object marshalling/unmarshalling implementations.

squbs provides marshallers for cross-language environments. For instance, when working with a mixed object hierarchy having both Scala case classes as well as Java beans. For simple, single-language environments, please use the provided marshallers or other open source marshallers directly.

In addition, squbs also adds a Java API for manual marshalling/marshalling. Manual access to marshallers and unmarshallers is useful for stream-based applications where some work may need to be done in a stream stage. It is also useful for testing marshaller configurations to ensure the right format is achieved.

This document discusses the marshallers and unmarshallers provided by squbs, and the facilities you can use to invoke these marshallers and unmarshallers manually. This document **does not** address the use of marshallers and unmarshallers as part of the pekko HTTP Routing DSL. Please see the pekko HTTP Routing DSL [Scala](http://doc.pekko.io/docs/pekko-http/current/scala/http/routing-dsl/directives/marshalling-directives/index.html#marshallingdirectives) and [Java](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/directives/marshalling-directives/index.html#marshallingdirectives-java) marshalling directives for using marshallers, including ones provided in this document, in the Routing DSL.

### Dependency

Add the following dependencies to your `build.sbt` or scala build file:

```scala
"org.squbs" %% "squbs-ext" % squbsVersion,
"de.heikoseeberger" %% "pekko-http-json4s" % pjfanningpekkoHttpJsonV,
"de.heikoseeberger" %% "pekko-http-jackson" % pjfanningpekkoHttpJsonV,
"com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
"com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
```

The followings are optional components depending on your marshalling formats and libraries you want to use. They may be combined to support multiple formats.

```scala
// To use json4s native...
"org.json4s" %% "json4s-native" % "3.5.0",

// To use json4s jackson...
"org.json4s" %% "json4s-jackson" % "3.5.0",
  
// For Jackson marshalling of Scala case classes... 
"com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  
// For Jackson marshalling of immutable Java classes...
"com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonVersion,
```  

### Usage

#### JacksonMapperSupport

The `JacksonMapperSupport` provides JSON marshallers/unmarshallers based on the popular Jackson library. It allows global as well as per-type configuration of Jackson `ObjectMapper`s.

Please see [Jackson Data Binding documentation](http://wiki.fasterxml.com/JacksonFAQ#Data_Binding.2C_general) for detail on `ObjectMapper` configuration.

##### Scala

You just need to import the `JacksonMapperSupport._` to expose its implicit members in the scope of marshaller/unmarshaller usage in Scala code:

```scala
import org.squbs.marshallers.json.JacksonMapperSupport._
```

Both automatic and manual marshallers will implicitly make use of the marshallers provided by this package. The following code shows various ways to configure the `DefaultScalaModule` with the `ObjectMapper`:

```scala
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.squbs.marshallers.json.JacksonMapperSupport

/* To use DefaultScalaModule for all classes.
 */

JacksonMapperSupport.setDefaultMapper(
  new ObjectMapper().registerModule(DefaultScalaModule))


/* To register a 'DefaultScalaModule' for marshalling a
 * specific class, overrides global configuration for
 * this class.
 */

JacksonMapperSupport.register[MyScalaClass](
  new ObjectMapper().registerModule(DefaultScalaModule))
```

##### Java

The marshallers and unmarshallers can be obtained from the `marshaller` and `unmarshaller` methods in `JacksonMapperSupport`, passing the class instance of the type to marshal/unmarshal as follows:

```java
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;

import static org.squbs.marshallers.json.JacksonMapperSupport.*;

Marshaller<MyClass, RequestEntity> myMarshaller =
    marshaller(MyClass.class);

Unmarshaller<HttpEntity, MyClass> myUnmarshaller =
    unmarshaller(MyClass.class);
```

These marshallers and unmarshallers can be used as part of the [pekko HTTP Routing DSL](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/overview.html) or as part of [invoking marshalling/unmarshalling](#invoking-marshallingunmarshalling) discussed in this document, below.

The following examples configure the `DefaultScalaModule` with the `ObjectMapper`:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import org.squbs.marshallers.json.JacksonMapperSupport;

/* Globally registers the 'DefaultScalaModule'.
 */
 
JacksonMapperSupport.setDefaultMapper(
  new ObjectMapper().registerModule(new DefaultScalaModule()));


/* This example below registers the 'DefaultScalaModule'
 * just for 'MyClass'
 */

JacksonMapperSupport.register(MyClass.class
  new ObjectMapper().registerModule(new DefaultScalaModule()));
```


#### XLangJsonSupport

The XLangJsonSupport adds cross language support by delegating marshalling and unmarshalling to:

* Json4s for Scala classes
* Jackson for Java classes

These are generally the preferred marshallers for each language as they support language-specific conventions without further configuration. They are also generally better optimized for the different conventions.

However, the decision to use Json4s or Jackson is made from the type of the object passed in for marshalling/unmarshalling. If you have a mixed object hierarchy you may still need to configure the marshalling/unmarshalling facilities to support different conventions as illustrated in the followings:

* Scala case class referencing Java Beans. Since the top-level object is of a Scala case class, Json4s will be chosen. But it does not know how to marshal/unmarshal Java Beans. A custom serializer needs to be added to Json4s to handle these Java Beans.
* Java Beans referencing Scala case class. Since the top-level object is a Java Bean, Jackson will be chosen. Jackson by default does not know how to marshal/unmarshal case classes. You need to register `DefaultScalaModule` to the Jackson `ObjectMapper` to handle such cases.

A general guideline for marshalling/unmarshalling mixed language  object hierarchies: Unless Json4s optimizations are preferred, it is easier to configure Jackson to handle Scala by just registering `DefaultScalaModule` to the `ObjectMapper`.

Like `JacksonMapperSupport`, it supports per-type configuration of the marshaller and unmarshaller. It allows configuration both for Json4s and Jackson.

##### Scala

You just need to import the `XLangJsonSupport._` to expose its implicit members in the scope of marshaller/unmarshaller usage in Scala code:

```scala
import org.squbs.marshallers.json.XLangJsonSupport._
```

Both automatic and manual marshallers will implicitly make use of the marshallers provided by this package. The following provide samples of configuring `XLangJsonSupport`:

```scala
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import org.json4s.{DefaultFormats, jackson, native}
import org.squbs.marshallers.json.XLangJsonSupport

/* The following configures the default settings
 * for 'XLangJsonSupport'
 */

// Adds ParameterNamesModule to Jackson
XLangJsonSupport.setDefaultMapper(
  new ObjectMapper().registerModule(new ParameterNamesModule())
  
// Tells Json4s to use native serialization
XLangJsonSupport.setDefaultSerialization(native.Serialization)

// Adds MySerializer to the serializers used by Json4s
XLangJsonSupport.setDefaultFormats(DefaultFormats + MySerializer)


/* The following configures XLangJsonSupport for specific class.
 * Namely, it configures for 'MyClass' and 'MyOtherClass'.
 */

// Use ParameterNamesModule for mashal/unmarshal MyClass
XLangJsonSupport.register[MyClass](new ParameterNamesModule()))

// Use Json4s Jackson serialization for MyOtherClass
XLangJsonSupport.register[MyOtherClass](jackson.Serialization)

// Use MySerializer Json4s serializer for MyOtherClass
XLangJsonSupport.register[MyOtherClass](DefaultFormats + MySerializer)
```

##### Java

The marshallers and unmarshallers can be obtained from the `marshaller` and `unmarshaller` methods in `XLangJsonSupport`, passing the class instance of the type to marshal/unmarshal as follows:

```java
import org.apache.pekko.http.javadsl.marshalling.Marshaller;
import org.apache.pekko.http.javadsl.model.HttpEntity;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;

import static org.squbs.marshallers.json.XLangJsonSupport.*;

Marshaller<MyClass, RequestEntity> myMarshaller =
    marshaller(MyClass.class);

Unmarshaller<HttpEntity, MyClass> myUnmarshaller =
    unmarshaller(MyClass.class);
```

These marshallers and unmarshallers can be used as part of the [pekko HTTP Routing DSL](http://doc.pekko.io/docs/pekko-http/current/java/http/routing-dsl/overview.html) or as part of [invoking marshalling/unmarshalling](#invoking-marshallingunmarshalling) discussed in this document, below.

The following provide samples of configuring XLangJsonSupport:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.squbs.marshallers.json.XLangJsonSupport;

/* Global XLangJsonSupport Configuration.
 */

// Adds ParameterNamesModule to Jackson
XLangJsonSupport.setDefaultMapper(
  new ObjectMapper().registerModule(new ParameterNamesModule());
  
// Tells Json4s to use native serialization
XLangJsonSupport.setDefaultSerialization(XLangJsonSupport.nativeSerialization());

// Adds MySerializer and MyOtherSerializer (varargs) to the serializers used by Json4s
XLangJsonSupport.addDefaultSerializers(new MySerializer(), new MyOtherSerializer());


/* Per-class configuration of 'XLangJsonSupport'.
 * In this case we show configuring 'MyClass' and 'MyOtherClass'
 */

// Use ParameterNamesModule for mashal/unmarshal MyClass
XLangJsonSupport.register(MyClass.class, new ParameterNamesModule()));

// Use Json4s Jackson serialization for MyOtherClass
XLangJsonSupport.register(MyOtherClass.class, XLangJsonSupport.jacksonSerialization());

// Adds MySerializer and MyOtherSerializer (varargs) to the serializers used by Json4s for MyOtherClass
XLangJsonSupport.addSerializers(MyOtherClass.class, new MySerializer(), new MyOtherSerializer());
```

#### Invoking Marshalling/Unmarshalling

Besides using marshallers and marshallers as part of pekko HTTP Routing DSL, manual invocation of marshalling and unmarshalling is often required for use in both server-side and client-side `Flow`s as well as for testing.

##### Scala

pekko provides a great [Scala DSL for marshalling and unmarshalling](http://doc.pekko.io/docs/pekko-http/current/scala/http/common/marshalling.html#using-marshallers). Its use can be seen in the example below:

```scala
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.MessageEntity
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal

// We need the ActorSystem and Materializer to marshal/unmarshal
implicit val system = ActorSystem()

// Also need the implicit marshallers provided by this import
import org.squbs.marshallers.json.XLangJsonSupport._

// Just call Marshal or Unmarshal as follows:
Marshal(myObject).to[MessageEntity]
Unmarshal(entity).to[MyType]
```

##### Java
The `MarshalUnmarshal` utility class is used for manually marshalling and unmarshalling objects using any `Marshaller` and `Unmarshaller` defined in pekko HTTP's JavaDSL. It's use can be seen in the example below:

```java
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.RequestEntity;
import org.apache.pekko.stream.Materializer;

import org.squbs.marshallers.MarshalUnmarshal;

// We're using JacksonMapperSupport here.
// But XLangJsonSupport works the same.
import static org.squbs.marshallers.json.JacksonMapperSupport.*;

// Base infrastructure, and the 'mu' MarshalUnmarshal. 
private final ActorSystem system = ActorSystem.create();
private final Materializer mat = Materializer.createMaterializer(system);
private final MarshalUnmarshal mu = new MarshalUnmarshal(system.dispatcher(), mat);

// Call 'apply' passing marshaller or unmarshaller as follows, using marshaller
// and unmarshaller methods from 'import static JacksonMapperSupport.*;':
CompletionStage<RequestEntity> mf = mu.apply(marshaller(MyClass.class), myObject);
CompletionStage<MyClass> uf = mu.apply(unmarshaller(MyClass.class), entity);
```
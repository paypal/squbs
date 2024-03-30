# Deduplicate Stage

### Overview

`Deduplicate` is an Pekko Streams `GraphStage` to drop identical (consecutive or non-consecutive) elements  in a stream.

### Dependency

Add the following dependency to your `build.sbt` or scala build file:

```
"org.squbs" %% "squbs-ext" % squbsVersion
```

### Usage

The usage is very similar to standard Pekko Stream stages:

```scala
val result = Source("a" :: "b" :: "b" :: "c" :: "a" :: "a" :: "a" :: "c" :: Nil).
  via(Deduplicate()).
  runWith(Sink.seq)
  
// Output: ("a" :: "b" :: "c" :: Nil)
```

`Deduplicate` keeps a registry of already seen elements.  To prevent the registry growing unboundedly, it allows to specify the number of duplicates for each message.  Once `duplicateCount` is reached that element is removed from the registry.  In the following example, `duplicateCount` is specified as `2`, so, `"a"` will not be dropped when seen the third time: 

```scala
val result = Source("a" :: "b" :: "b" :: "c" :: "a" :: "a" :: "a" :: "c" :: Nil).
  via(Deduplicate(2)).
  runWith(Sink.seq)
  
// Output: ("a" :: "b" :: "c" :: "a" :: Nil)
```

Please note, `duplicateCount` prevents registry from ever growing when the number of duplicates are known.  However, there is still the potential of memory leaks.  For instance, if `duplicateCount` is set to `2`, an element will be kept in the registry until the duplicate is seen; however, there might be scenarios where duplicate never shows up, e.g., a `filter` or `drop` is used.  So, be aware of consequences of `Duplicate` in your use case.

You can also provide a different registry implementation to `Deduplicate` that cleans itself periodically.  But, you should do this only if you are certain that a duplicate would not be seen after a given time frame; otherwise, the duplication logic might be corrupted.

### Configuring registry key and registry implementation

`Deduplicate` uses the element itself as the key to the registry by default.  However, it also accepts a function to map to a key from the element.  For instance, if the elements in the stream are tuples of type `(Int, String)` and you would like to identify duplicates only based on the first field of the tuple, you can pass a function as follows:

```scala
val deduplicate = Deduplicate((element: (Int, String)) => element._1, 2)
```  

`Deduplicate` also allows the registry to replaced with a different implementation of type `java.util.Map[Key, MutableLong]`.

```scala
val deduplicate = Deduplicate(2, new util.TreeMap[String, MutableLong]())
```
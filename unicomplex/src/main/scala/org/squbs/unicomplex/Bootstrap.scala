package org.squbs.unicomplex

import java.io.{File, FileInputStream}
import java.util.jar.{JarFile, Manifest}

import concurrent.Await
import concurrent.duration._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

object Bootstrap extends App {
    
  println("Booting unicomplex")
  
  val cubes = System.getProperty("java.class.path")
		  		.split(File.pathSeparator)
		  		.map(getManifestInfo).filter(_ != null)
		  		.map(getNameAndBootClass).filter(_ != null)


  private[this] def getManifestInfo(jarName : String) : (String, Manifest) = {
    val jarFile = new File(jarName)
    var stream : FileInputStream = null
    try {
	  if (jarFile.isDirectory) {
	    val manifestFile = new File(jarName, "META-INF/MANIFEST.MF")
        if (manifestFile.isFile) {
          stream = new FileInputStream(manifestFile)
          (jarName, new Manifest(stream))
        } else null
	  } else if (jarFile.isFile) {
	    val manifest = new JarFile(jarFile).getManifest
	    if (manifest == null) null else (jarName, manifest)
	  } else null
	} catch {
	 case e : Exception =>
	    println(s"${e.getClass.getName} reading manifest from $jarName.\n ${e.getMessage}")
	    null
	} finally {
	  if (stream != null) stream.close
	}
  }		  		

  private[this] def getNameAndBootClass(manifestInfo : (String, Manifest)) : (String, String, Class[_]) = {
    val attrs = manifestInfo._2.getMainAttributes      
    val symName = attrs.getValue("Bundle-SymbolicName")
    val version = attrs.getValue("Bundle-Version")
    val lifecycle = attrs.getValue("X-squbs-lifecycle")
    val routeDef = attrs.getValue("X-squbs-service")
    if (lifecycle != null) {
      try {
        val clazz = Class.forName(lifecycle + '$', true, getClass.getClassLoader)
        println(s"Started ${symName} ${version}")
        (symName, version, clazz)
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          println(s"Can't load lifecycle object $lifecycle.\n" +
        		  s"Cube: $symName $version\n" +
    		      s"Path: ${manifestInfo._1}\n" +
    		      s"${t.getClass.getName}: ${t.getMessage}")        
          null
      } 
    } else if (routeDef != null) {
      try {
        import ServiceRegistry.registrar
        val clazz = Class.forName(routeDef, true, getClass.getClassLoader)
        val routeClass = clazz.asSubclass(classOf[RouteDefinition])        
        if (registrar() == null) {
          val startTime = System.nanoTime
          implicit val timeout = Timeout(1000 milliseconds)
          val ackFuture = Unicomplex.uniActor ? StartWebService
          // Block for the web service to be started.
          Await.ready(ackFuture, timeout.duration)
          // Tight loop making sure the registrar is in place
          while (registrar() == null) {
            registrar.await
          }
          val elapsed = (System.nanoTime - startTime) / 1000
          println(s"Web Service started in $elapsed microseconds")
        }
        registrar() ! Register(routeClass.newInstance)
        println(s"Started ${symName} ${version}")
        (symName, version, clazz)
      } catch {
        case e: Exception =>
          val t = getRootCause(e)
          println(s"Can't load route definition $routeDef.\n" +
        		  s"Cube: $symName $version\n" +
    		      s"Path: ${manifestInfo._1}\n" +
    		      s"${t.getClass.getName}: ${t.getMessage}")        
          null          
      }
    } else null
  }
  
  private[this] def getRootCause(e: Exception) = {
    var t : Throwable = e
    var cause = e.getCause
    while (cause != null) {
      t = cause
      cause = t.getCause
    }
    t
  }
}	

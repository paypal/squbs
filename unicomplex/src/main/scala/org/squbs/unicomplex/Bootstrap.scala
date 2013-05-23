package org.squbs.unicomplex

import java.io._
import java.util.jar._
import io.Source

object Bootstrap extends App {
  
  println("Booting unicomplex")  
  
  val cubes = System.getProperty("java.class.path")
		  		.split(File.pathSeparator)
		  		.map(getManifestInfo).filter(_ != null)
		  		.map(getNameAndBootClass).filter(_ != null)
		  		
  def getManifestInfo(jarName : String) : (String, Manifest) = {
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

  def getNameAndBootClass(manifestInfo : (String, Manifest)) : (String, String, Class[_]) = {
    val attrs = manifestInfo._2.getMainAttributes      
    val symName = attrs.getValue("Bundle-SymbolicName")
    val version = attrs.getValue("Bundle-Version")
    val lifecycle = attrs.getValue("X-squbs-lifecycle")    
    if (lifecycle == null) null
    else
     try {
       val clazz = Class.forName(lifecycle + '$', true, getClass.getClassLoader)
        println(s"Started ${symName} ${version}")
        (symName, version, clazz)
     } catch {
        case e: Exception =>
          var t : Throwable = e
          var cause = e.getCause
          while (cause != null) {
            t = cause
            cause = t.getCause
          }
          println(s"Can't load lifecycle object $lifecycle.\n" +
        		  s"Cube: $symName $version\n" +
    		      s"Path: ${manifestInfo._1}\n" +
    		      s"${t.getClass.getName}: ${t.getMessage}")        
          null
    }
  }
}	

package org.squbs.httpclient.json

/**
 * Created by lma on 6/23/2015.
 */
object ReflectHelper {

  import scala.reflect.runtime.universe._
  //TODO cache?
  def isJavaClass(v: Any): Boolean = {
    val typeMirror = runtimeMirror(v.getClass.getClassLoader)
    val instanceMirror = typeMirror.reflect(v)
    val symbol = instanceMirror.symbol
    symbol.isJava
  }

  def isJavaClass(clazz: Class[_]): Boolean = {
    val typeMirror = runtimeMirror(clazz.getClassLoader)
    val classMirror = typeMirror.classSymbol(clazz)
    classMirror.isJava
  }
}

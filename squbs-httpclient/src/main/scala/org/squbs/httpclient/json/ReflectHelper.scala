package org.squbs.httpclient.json

import scala.reflect.{ManifestFactory, ClassTag}

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


  def toManifest[T: TypeTag]: Manifest[T] = {
    val t = typeTag[T]
    val mirror = t.mirror
    def toManifestRec(t: Type): Manifest[_] = {
      val clazz = ClassTag[T](mirror.runtimeClass(t)).runtimeClass
      if (t.typeArgs.length == 1) {
        val arg = toManifestRec(t.typeArgs.head)
        ManifestFactory.classType(clazz, arg)
      } else if (t.typeArgs.length > 1) {
        val args = t.typeArgs.map(x => toManifestRec(x))
        ManifestFactory.classType(clazz, args.head, args.tail: _*)
      } else {
        ManifestFactory.classType(clazz)
      }
    }
    toManifestRec(t.tpe).asInstanceOf[Manifest[T]]
  }

}

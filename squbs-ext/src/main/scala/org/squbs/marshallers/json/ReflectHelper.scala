/*
 *  Copyright 2017 PayPal
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.squbs.marshallers.json

import scala.reflect.{ClassTag, ManifestFactory, api}
import scala.reflect.runtime.universe._

object ReflectHelper {

  //TODO cache?
  private[marshallers] def isJavaClass(v: Any): Boolean = {
    val typeMirror = runtimeMirror(v.getClass.getClassLoader)
    val instanceMirror = typeMirror.reflect(v)
    val symbol = instanceMirror.symbol
    symbol.isJava
  }

  private[marshallers] def isJavaClass(clazz: Class[_]): Boolean = {
    val typeMirror = runtimeMirror(clazz.getClassLoader)
    val classMirror = typeMirror.classSymbol(clazz)
    classMirror.isJava
  }


  private[marshallers] def toManifest[T: TypeTag]: Manifest[T] = {
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

  private[marshallers] def toClass[T: TypeTag]: Class[T] = {
    val t = typeTag[T]
    val mirror = t.mirror
    mirror.runtimeClass(t.tpe).asInstanceOf[Class[T]]
  }

  private[marshallers] def toClassTag[T: TypeTag]: ClassTag[T] = {
    val t = typeTag[T]
    val mirror = t.mirror
    ClassTag[T](mirror.runtimeClass(t.tpe))
  }

  private[marshallers] def toTypeTag[T](clazz: Class[T]): TypeTag[T] = {
    val mirror = runtimeMirror(clazz.getClass.getClassLoader)
    val tpe = mirror.classSymbol(clazz).toType
    TypeTag(mirror, new api.TypeCreator {
      def apply[U <: api.Universe with Singleton](m: api.Mirror[U]) =
        if (m eq mirror) tpe.asInstanceOf[U#Type]
        else throw new IllegalArgumentException(s"Type tag defined in $mirror cannot be migrated to other mirrors.")
    })
  }
}

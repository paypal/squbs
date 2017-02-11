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

import org.scalatest.{FlatSpec, Matchers}
import org.squbs.marshallers.json.TestData._

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class ReflectHelperSpec extends FlatSpec with Matchers{

  it should "determine an object is Scala or Java" in {
    ReflectHelper.isJavaClass(fullTeamWithPrivateMembers) shouldBe true
    ReflectHelper.isJavaClass(fullTeam) shouldBe false
  }

  it should "determine a class is Scala or Java" in {
    ReflectHelper.isJavaClass(classOf[TeamWithPrivateMembers]) shouldBe true
    ReflectHelper.isJavaClass(classOf[Team]) shouldBe false
  }

  it should "convert TypeTag to Manifest for any type" in {

    def assertTypeTagToManifestConversion[T](implicit typeTag: TypeTag[T], manifest: Manifest[T]) =
      ReflectHelper.toManifest[T] shouldBe manifest

    assertTypeTagToManifestConversion[Team]
    assertTypeTagToManifestConversion[List[Employee]]
    assertTypeTagToManifestConversion[Map[String, Seq[Employee]]]
  }

  it should "find the right class given a type and TypeTag, with erasure" in {
    ReflectHelper.toClass[Team] shouldBe classOf[Team]
    ReflectHelper.toClass[immutable.Seq[Employee]] shouldBe classOf[immutable.Seq[_]]
  }

  it should "convert TypeTag to ClassTag for any type, with erasure" in {
    ReflectHelper.toClassTag[Team] shouldBe ClassTag[Team](classOf[Team])
    ReflectHelper.toClassTag[immutable.Seq[Employee]] shouldBe ClassTag[immutable.Seq[_]](classOf[immutable.Seq[_]])
  }
}

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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.{HttpEntity, MediaTypes, MessageEntity}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.json4s.{DefaultFormats, jackson, native}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.marshallers.json.TestData._

class XLangJsonSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import XLangJsonSupport._

  implicit val system = ActorSystem("XLangJsonSpec")

  it should "marshal and unmarshal standard case classes" in {
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeam).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[Team] map { _ shouldBe fullTeam }
  }

  it should "marshal and unmarshal Scala non-case classes" in {
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeamNonCaseClass).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[TeamNonCaseClass] map { _ shouldBe fullTeamNonCaseClass }
  }

  it should "marshal and unmarshal Scala class with Java Bean members" in {
    XLangJsonSupport.register[TeamWithBeanMember](DefaultFormats + EmployeeBeanSerializer)
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeamWithBeanMember).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[TeamWithBeanMember] map { _ shouldBe fullTeamWithBeanMember }
  }

  it should "marshal and unmarshal Java Bean with case class members" in {
    val caseClassMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    XLangJsonSupport.register[TeamBeanWithCaseClassMember](caseClassMapper)
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeamWithCaseClassMember).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[TeamBeanWithCaseClassMember] map { _ shouldBe fullTeamWithCaseClassMember }
  }

  it should "marshal and unmarshal Java Bean" in {
    val fieldMapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
      .registerModule(DefaultScalaModule)
    XLangJsonSupport.register[TeamWithPrivateMembers](fieldMapper)
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeamWithPrivateMembers).to[MessageEntity] map { _ shouldBe entity }
    //Unmarshal(entity).to[TeamWithPrivateMembers] map { _ shouldBe fullTeamWithPrivateMembers }
  }

  it should "Marshal and unmarshal Jackson annotated Java subclasses" in {
    val entity = HttpEntity(MediaTypes.`application/json`, pageTestJson)
    Marshal(pageTest).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[PageData] map { _ shouldBe pageTest }
  }

  it should "Register a new per-class serialization for Json4s" in {
    XLangJsonSupport.register[Employee](native.Serialization)
    val assertionResult =
      XLangJsonSupport.serializations should contain (classOf[Employee] -> native.Serialization)
    XLangJsonSupport.serializations -= classOf[Employee]
    assertionResult
  }

  it should "Register a new per-class native serialization for Json4s using Java API" in {
    XLangJsonSpecHelper.registerNativeSerializer()
    val assertionResult =
      XLangJsonSupport.serializations should contain (classOf[EmployeeBean] -> native.Serialization)
    XLangJsonSupport.serializations -= classOf[EmployeeBean]
    assertionResult
  }

  it should "Register a new per-class jackson serialization for Json4s using Java API" in {
    XLangJsonSpecHelper.registerJacksonSerializer()
    val assertionResult =
      XLangJsonSupport.serializations should contain (classOf[EmployeeBean] -> jackson.Serialization)
    XLangJsonSupport.serializations -= classOf[EmployeeBean]
    assertionResult
  }

  it should "Add a new default serializer for Json4s and reset to default" in {
    XLangJsonSpecHelper.addDefaultSerializer()
    XLangJsonSupport.globalDefaultFormats.customSerializers shouldBe List(EmployeeBeanSerializer)
    XLangJsonSupport.setDefaultFormats(DefaultFormats)
    XLangJsonSupport.globalDefaultFormats shouldBe DefaultFormats
  }

  it should "Set a new default serialization for Json4s and reset to default" in {
    XLangJsonSupport.setDefaultSerialization(native.Serialization)
    XLangJsonSupport.defaultSerialization shouldBe native.Serialization
    XLangJsonSupport.setDefaultSerialization(jackson.Serialization)
    XLangJsonSupport.defaultSerialization shouldBe jackson.Serialization
  }

  it should "Set a new default object mapper for Jackson and reset to default" in {
    val scalaMapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val defaultMapper = new ObjectMapper()
    XLangJsonSupport.setDefaultMapper(scalaMapper)
    JacksonMapperSupport.defaultMapper shouldBe scalaMapper
    XLangJsonSupport.setDefaultMapper(defaultMapper)
    JacksonMapperSupport.defaultMapper shouldBe defaultMapper
  }

  "defaultFormats" should "return DefaultFormats" in {
    XLangJsonSupport.defaultFormats shouldBe DefaultFormats
  }
}

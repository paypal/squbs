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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.squbs.marshallers.json.TestData._

class JacksonMapperSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  import JacksonMapperSupport._

  implicit val system = ActorSystem("JacksonMapperSpec")
  JacksonMapperSupport.setDefaultMapper(new ObjectMapper().registerModule(DefaultScalaModule))

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
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeamWithBeanMember).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[TeamWithBeanMember] map { _ shouldBe fullTeamWithBeanMember }
  }

  it should "marshal and unmarshal Java Bean with case class members" in {
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeamWithCaseClassMember).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[TeamBeanWithCaseClassMember] map { _ shouldBe fullTeamWithCaseClassMember }
  }

  it should "marshal and unmarshal Java Bean" in {
    val fieldMapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
      .registerModule(DefaultScalaModule)
    JacksonMapperSupport.register[TeamWithPrivateMembers](fieldMapper)
    val entity = HttpEntity(MediaTypes.`application/json`, fullTeamJson)
    Marshal(fullTeamWithPrivateMembers).to[MessageEntity] map { _ shouldBe entity }
    //Unmarshal(entity).to[TeamWithPrivateMembers] map { _ shouldBe fullTeamWithPrivateMembers }
  }

  it should "Marshal and unmarshal Jackson annotated Java subclasses" in {
    JacksonMapperSupport.register[PageData](new ObjectMapper)
    val entity = HttpEntity(MediaTypes.`application/json`, pageTestJson)
    Marshal(pageTest).to[MessageEntity] map { _ shouldBe entity }
    Unmarshal(entity).to[PageData] map { _ shouldBe pageTest }
  }
}

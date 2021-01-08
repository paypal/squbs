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

import java.util

import org.json4s.CustomSerializer
import org.json4s.JsonAST._
import scala.collection.JavaConverters._

object TestData {
  val fullTeamWithPrivateMembers: TeamWithPrivateMembers = {
    val list = new util.ArrayList[EmployeeBean]()
    list.add(new EmployeeBean(1, "John", "Doe", 20, true))
    list.add(new EmployeeBean(2, "Mike", "Moon", 25, true))
    list.add(new EmployeeBean(3, "Jane", "Williams", 30, false))
    list.add(new EmployeeBean(4, "Liz", "Taylor", 35, false))

    new TeamWithPrivateMembers("squbs Team", list)
  }

  val fullTeamNonCaseClass: TeamNonCaseClass = new TeamNonCaseClass("squbs Team", List[EmployeeNonCaseClass](
    new EmployeeNonCaseClass(1, "John", "Doe", 20, male = true),
    new EmployeeNonCaseClass(2, "Mike", "Moon", 25, male = true),
    new EmployeeNonCaseClass(3, "Jane", "Williams", 30, male = false),
    new EmployeeNonCaseClass(4, "Liz", "Taylor", 35, male = false)
  ))

  //scala class use java bean
  val fullTeamWithBeanMember: TeamWithBeanMember = new TeamWithBeanMember("squbs Team", List[EmployeeBean](
    new EmployeeBean(1, "John", "Doe", 20, true),
    new EmployeeBean(2, "Mike", "Moon", 25, true),
    new EmployeeBean(3, "Jane", "Williams", 30, false),
    new EmployeeBean(4, "Liz", "Taylor", 35, false)
  ))

  val fullTeamWithCaseClassMember: TeamBeanWithCaseClassMember =
    new TeamBeanWithCaseClassMember("squbs Team", List[Employee](
      Employee(1, "John", "Doe", 20, male = true),
      Employee(2, "Mike", "Moon", 25, male = true),
      Employee(3, "Jane", "Williams", 30, male = false),
      Employee(4, "Liz", "Taylor", 35, male = false)
    ).asJava)

  val fullTeam: Team = Team("squbs Team", List[Employee](
    Employee(1, "John", "Doe", 20, male = true),
    Employee(2, "Mike", "Moon", 25, male = true),
    Employee(3, "Jane", "Williams", 30, male = false),
    Employee(4, "Liz", "Taylor", 35, male = false)
  ))

  val pageTest: PageData = new PageData(100, Seq("one", "two", "three").asJava)

  val newTeamMember: Employee = Employee(5, "Jack", "Ripper", 35, male = true)
  val newTeamMemberBean: EmployeeBean = new EmployeeBean(5, "Jack", "Ripper", 35, true)

  val fullTeamJson: String = "{\"description\":\"squbs Team\",\"members\":[{\"id\":1,\"firstName\":\"John\"," +
    "\"lastName\":\"Doe\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Mike\",\"lastName\":\"Moon\"," +
    "\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Jane\",\"lastName\":\"Williams\",\"age\":30,\"male\":false}," +
    "{\"id\":4,\"firstName\":\"Liz\",\"lastName\":\"Taylor\",\"age\":35,\"male\":false}]}"
  val fullTeamWithDelJson: String = "{\"description\":\"squbs Team\",\"members\":[{\"id\":1,\"firstName\":\"John\"," +
    "\"lastName\":\"Doe\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Mike\",\"lastName\":\"Moon\"," +
    "\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Jane\",\"lastName\":\"Williams\",\"age\":30,\"male\":false}]}"
  val fullTeamWithAddJson: String = "{\"description\":\"squbs Team\",\"members\":[{\"id\":1,\"firstName\":\"John\"," +
    "\"lastName\":\"Doe\",\"age\":20,\"male\":true},{\"id\":2,\"firstName\":\"Mike\",\"lastName\":\"Moon\"," +
    "\"age\":25,\"male\":true},{\"id\":3,\"firstName\":\"Jane\",\"lastName\":\"Williams\",\"age\":30,\"male\":false}," +
    "{\"id\":4,\"firstName\":\"Liz\",\"lastName\":\"Taylor\",\"age\":35,\"male\":false},{\"id\":5," +
    "\"firstName\":\"Jack\",\"lastName\":\"Ripper\",\"age\":35,\"male\":true}]}"
  val newTeamMemberJson: String = "{\"id\":5,\"firstName\":\"Jack\",\"lastName\":\"Ripper\",\"age\":35,\"male\":true}"
  val pageTestJson: String = "{\"start_page\":100,\"data_pages\":[\"one\",\"two\",\"three\"]}"

  val fullTeamWithDel: Team = Team("squbs Team", List[Employee](
    Employee(1, "John", "Doe", 20, male = true),
    Employee(2, "Mike", "Moon", 25, male = true),
    Employee(3, "Jane", "Williams", 30, male = false)
  ))

  val fullTeamWithAdd: Team = Team("squbs Team", List[Employee](
    Employee(1, "John", "Doe", 20, male = true),
    Employee(2, "Mike", "Moon", 25, male = true),
    Employee(3, "Jane", "Williams", 30, male = false),
    Employee(4, "Liz", "Taylor", 35, male = false),
    newTeamMember
  ))

  val fullTeamPrivateMembersWithAdd: TeamWithPrivateMembers = fullTeamWithPrivateMembers.addMember(newTeamMemberBean)

}

//case class reference case class
case class Employee(id: Long, firstName: String, lastName: String, age: Int, male: Boolean)

case class Team(description: String, members: List[Employee])

//non case class with accessor
class EmployeeNonCaseClass(val id: Long, val firstName: String, val lastName: String, val age: Int, val male: Boolean){
  override def equals(obj : Any) : Boolean  = {
    obj match {
      case t : EmployeeNonCaseClass =>
        t.id == id && t.firstName == firstName && t.lastName == lastName && t.age == age && t.male == male
      case _ => false
    }
  }

  override def hashCode(): Int =
    id.hashCode() + firstName.hashCode() + lastName.hashCode() + age.hashCode() + male.hashCode()
}

class TeamNonCaseClass(val description: String, val members: List[EmployeeNonCaseClass]){
  override def equals(obj : Any) : Boolean  = {
    obj match {
      case t : TeamNonCaseClass =>
        t.description == description && t.members == members
      case _ => false
    }
  }

  override def hashCode(): Int = description.hashCode() + (members map (_.hashCode())).sum
}

object EmployeeBeanSerializer extends CustomSerializer[EmployeeBean]( _ => (
  { case JObject(JField("id", JInt(i)) :: JField("firstName", JString(f)) :: JField("lastName", JString(l)) :: JField(
  "age", JInt(a)) :: JField("male", JBool(m)) :: Nil) =>
    new EmployeeBean(i.longValue, f, l, a.intValue, m)
  },
  { case x: EmployeeBean =>
    JObject(
      JField("id", JInt(BigInt(x.getId))) ::
        JField("firstName", JString(x.getFirstName)) ::
        JField("lastName", JString(x.getLastName)) ::
        JField("age", JInt(x.getAge)) ::
        JField("male", JBool(x.isMale)) ::
        Nil)
  })
) {

  /**
    * Allows Java to get hold of this object instance easily.
    * @return This object's singleton instance.
    */
  def getInstance: EmployeeBeanSerializer.type = this
}

//scala class reference java class
class TeamWithBeanMember(val description: String, val members: List[EmployeeBean]) {

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: TeamWithBeanMember =>
        other.description == description && other.members == members
      case _ => false
    }
  }

  override def hashCode(): Int = description.hashCode + members.hashCode
}


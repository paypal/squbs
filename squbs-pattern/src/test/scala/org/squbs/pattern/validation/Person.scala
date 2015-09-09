package org.squbs.pattern.validation

case class Person(firstName: String, lastName: String, middleName: Option[String] = None, age: Int)

object SampleValidators {

  import com.wix.accord.dsl._
  implicit val personValidator = com.wix.accord.dsl.validator[ Person ] { p =>
                p.firstName as "First Name" is notEmpty
                p.lastName as "Last Name" is notEmpty
                p.middleName.each is notEmpty // If exists, should not be empty.
                p.age should be >= 0
              }
}
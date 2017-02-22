
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

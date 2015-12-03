
/*
 *  Copyright 2015 PayPal
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

import com.wix.accord.Validator
import spray.routing.Directives._
import spray.routing._

trait ValidationDirectives {
  def validate(magnet: ValidationMagnet) = magnet()
}

// Later on we can rename it to squbsDirectives, when we have more maybe ?
object ValidationDirectives extends ValidationDirectives

/**
 * @see <a href="http://spray.io/blog/2012-12-13-the-magnet-pattern">Magnet Pattern</a>
 */
sealed trait ValidationMagnet {
  def apply(): Directive0
}

object ValidationMagnet {
  implicit def fromObj[T](obj: T)(implicit validator: Validator[ T ]) =
    new ValidationMagnet {
      def apply() = {

        val result = com.wix.accord.validate(obj)

        result match {
          case com.wix.accord.Success => pass
          case com.wix.accord.Failure(violations) => reject(ValidationRejection(violations flatMap {violation => violation.description} mkString(", ")))
        }
      }
    }
}
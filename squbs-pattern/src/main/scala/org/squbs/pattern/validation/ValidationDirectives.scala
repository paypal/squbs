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
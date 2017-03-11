/*
 * Copyright 2017 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.squbs.streams

import java.util.Optional
import java.util.function.{Function => JFunction}

object UniqueId {

  /**
    * A message type can implement this interface to map itself to a unique id.
    *
    * If a message cannot implement this interface, it is also possible to wrap the message in [[Envelope]].
    */
  trait Provider {
    def uniqueId: Any
  }

  /**
    * If a message cannot implement [[Provider]], it can also be wrapped with this envelope to provide a
    * unique id along with it.
    */
  final case class Envelope(message: Any, id: Any) extends Provider {
    override def uniqueId: Any = id
  }

  private[squbs] def javaUniqueIdMapperAsScala[Context](uniqueIdMapper: JFunction[Context, Optional[Any]]):
  Context => Option[Any] = {

    import scala.compat.java8.FunctionConverters._
    import scala.compat.java8.OptionConverters._

    (context: Context) => toScala(uniqueIdMapper.asScala(context))
  }
}
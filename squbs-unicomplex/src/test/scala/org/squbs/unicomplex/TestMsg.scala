package org.squbs.unicomplex

/**
 * Created by zhuwang on 2/21/14.
 */

object Constants {

  val SUFFIX = "$"
  val PREFIX = "^"
}

case class EchoMsg(msg: String)

case class AppendedMsg(msg: String)

case class PrependedMsg(msg: String)

case object Ping

case object Pong
package org.squbs.httpclient.dummy

import org.squbs.httpclient.env.{QA, PROD, Environment, EnvironmentResolver}

/**
 * Created by hakuang on 7/23/2014.
 */
object DummyProdEnvironmentResolver extends EnvironmentResolver {

  override def resolve(svcName: String): Option[Environment] = Some(PROD)

  override def name: String = "DummyProdEnvironmentResolver"
}

object DummyPriorityEnvironmentResolver extends EnvironmentResolver {

  override def resolve(svcName: String): Option[Environment] = svcName match {
    case "abc" => Some(QA)
    case _ => None
  }

  override def name: String = "DummyPriorityEnvironmentResolver"
}

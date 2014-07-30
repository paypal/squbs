package org.squbs.testkit

import org.squbs.unicomplex.{JMX, UnicomplexBoot, RouteDefinition}
import spray.routing.Directives
import spray.http.StatusCodes
import java.io.File
import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, FlatSpecLike}
import dispatch._
import scala.concurrent.Await
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.squbs.testkit.util.Ports

class CustomTestKitSpec extends CustomTestKit(CustomTestKitSpec.boot) with FlatSpecLike with Matchers with Eventually {

  override implicit val patienceConfig = new PatienceConfig(timeout = Span(3, Seconds))

  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global

  it should "return OK" in {
    eventually {
      val req = url(s"http://127.0.0.1:${CustomTestKitSpec.port}/test")
      val result = Await.result(Http(req OK as.String), 20 second)
      result should include("success")
    }
  }
}

object CustomTestKitSpec {

  import collection.JavaConversions._

  val port = Ports.available(3888, 5000)

  val testConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name" -> "myTest",
      "squbs.external-config-dir" -> "actorCalLogTestConfig",
      "default-listener.bind-port" -> Int.box(port),
      "squbs." + JMX.prefixConfig -> Boolean.box(true)
    )
  )

  lazy val boot = UnicomplexBoot(testConfig)
    .scanComponents(Seq(new File("testkit/src/test/resources/CustomTestKitTest").getAbsolutePath))
    .start()
}

class Service extends RouteDefinition with Directives {

  def route = get {
    complete(StatusCodes.OK, "success")
  }
}

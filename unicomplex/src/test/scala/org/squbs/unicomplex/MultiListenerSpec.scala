package org.squbs.unicomplex

import spray.routing.{Directives, Route}
import spray.http.StatusCodes
import org.scalatest.{Matchers, FlatSpec, BeforeAndAfterAll}

import akka.actor.ActorSystem
import java.io.File
import org.squbs.lifecycle.GracefulStop
import com.typesafe.config.ConfigFactory
import java.util.UUID
import dispatch._
import scala.concurrent.Await
import org.squbs._


class MultiListenerSpec extends FlatSpec with BeforeAndAfterAll with Matchers {

  import concurrent.duration._
  import concurrent.ExecutionContext.Implicits.global
  val port1 = nextPort
  val port2 = nextPort
  var boot: UnicomplexBoot = null

  it should "run up two listeners on different ports" in {
    val req1 = url(s"http://localhost:${port1}/multi")
    Await.result(Http(req1), 1 second).getStatusCode should be(200)
    val req2 = url(s"http://localhost:${port2}/multi")
    Await.result(Http(req2), 1 second).getStatusCode should be(200)
  }

  it should "only have started the application once" in {
    MultiListenerService.count should be(1)
  }

  override protected def beforeAll(): Unit = {
    val config = ConfigFactory.parseString(
      s"""
        squbs {
          actorsystem-name = "${UUID.randomUUID()}"
          ${JMX.prefixConfig} = true
        }
        default-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port = ${port1}
          bind-service = true
          secure = false
          client-authn = false
          ssl-context = default
        }
        second-listener {
          type = squbs.listener
          aliases = []
          bind-address = "0.0.0.0"
          full-address = false
          bind-port =  ${port2}
          bind-service = true
          secure = false
          client-authn = false
          ssl-context = default
        }
      """.stripMargin)
    boot = UnicomplexBoot(config)
      .createUsing { (name, config) => ActorSystem(name, config)}
      .scanComponents(Seq(new File("unicomplex/src/test/resources/classpaths/MultiListeners").getAbsolutePath))
      .initExtensions
      .start()
  }

  override protected def afterAll(): Unit = {
    Unicomplex(boot.actorSystem).uniActor ! GracefulStop
  }
}

class MultiListenerService extends RouteDefinition with Directives {
  MultiListenerService.inc


  override def route: Route = get {
    complete(StatusCodes.OK)
  }
}

object MultiListenerService {
  private var counter = 0

  def count = counter

  def inc: Unit = counter = counter + 1
}
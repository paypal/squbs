package org.squbs.unicomplex

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{WordSpec, BeforeAndAfterAll}
import java.io.{FileNotFoundException, File}
import org.scalatest.matchers.ClassicMatchers
import scala.concurrent.duration._
import org.scalatest.concurrent.AsyncAssertions
import scala.io.Source
import org.squbs.lifecycle.GracefulStop
import scala.concurrent.future

/**
 * Created by zhuwang on 2/21/14.
 */
class UnicomplexSpec extends TestKit(Unicomplex.actorSystem) with ImplicitSender
                             with WordSpec with ClassicMatchers with BeforeAndAfterAll
                             with AsyncAssertions {

  implicit val executionContext = system.dispatcher

  implicit val timeout: akka.util.Timeout = 2 seconds
  val dummyJarsDir = new File("unicomplex/src/test/resources/classpaths")

  override def beforeAll() = {
    if (dummyJarsDir.exists && dummyJarsDir.isDirectory) {
      val classpaths = dummyJarsDir.listFiles().map(_.getAbsolutePath).mkString(File.pathSeparator)
      System.setProperty("java.class.path", classpaths)
    }else {
      println("[UnicomplexSpec] There is no cube to be loaded")
    }

    Bootstrap.main(Array.empty[String])
  }

  override def afterAll() = {
    if (!system.isTerminated) {
      system.shutdown()
    }
  }

  "Bootstrap" must {

    "start all cube actors" in {
      assert(Bootstrap.actors.size == 3)

      val w = new Waiter
      system.actorSelection("/user/DummyCube").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })

      system.actorSelection("/user/DummyCube/Appender").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      system.actorSelection("/user/DummyCube/Prepender").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })
      system.actorSelection("/user/DummyCubeSvc/PingPongPlayer").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })

      w.await()
    }

    "start all services" in {
      assert(Bootstrap.services.size == 2)

      Thread.sleep(1000)

      assert(Source.fromURL("http://127.0.0.1:9090/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL("http://127.0.0.1:9090/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL("http://127.0.0.1:9090/pingpongsvc/pong").mkString equals "Ping")
    }
  }

  "UniComplex" must {

    "stop a single cube without affect other cubes" in {

      assert(Source.fromURL("http://127.0.0.1:9090/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL("http://127.0.0.1:9090/pingpongsvc/pong").mkString equals "Ping")

      val w = new Waiter

      Unicomplex.uniActor ! StopCube("org.squbs.unicomplex.test.DummyCubeSvc")

      Thread.sleep(1000)

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {result.isFailure}
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL("http://127.0.0.1:9090/dummysvc/msg/hello").mkString equals "^hello$")

      intercept[FileNotFoundException]{
        Source.fromURL("http://127.0.0.1:9090/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL("http://127.0.0.1:9090/pingpongsvc/pong").mkString
      }

    }

    "start a single cube correctly" in {
      val w = new Waiter

      intercept[FileNotFoundException]{
        Source.fromURL("http://127.0.0.1:9090/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL("http://127.0.0.1:9090/pingpongsvc/pong").mkString
      }

      Unicomplex.uniActor ! StartCube("org.squbs.unicomplex.test.DummyCubeSvc")

      Thread.sleep(1000)

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL("http://127.0.0.1:9090/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL("http://127.0.0.1:9090/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL("http://127.0.0.1:9090/pingpongsvc/pong").mkString equals "Ping")
    }

    "shutdown the system gracefully" in {

      (1 to 10) foreach {_ => future {Source.fromURL("http://127.0.0.1:9090/dummysvc/msg/hello")}}

      Unicomplex.uniActor ! GracefulStop

      awaitCond(system.isTerminated, 10 seconds, 2 seconds)
    }
  }
}

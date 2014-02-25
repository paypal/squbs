package org.squbs.unicomplex

import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{WordSpec, BeforeAndAfterAll}
import java.io.{FileNotFoundException, File}
import org.scalatest.matchers.ClassicMatchers
import scala.concurrent.duration._
import org.scalatest.concurrent.AsyncAssertions
import scala.io.Source
import org.squbs.lifecycle.GracefulStop
import scala.concurrent._
import akka.pattern.ask

/**
 * Created by zhuwang on 2/21/14.
 */
class UnicomplexSpec extends TestKit(Unicomplex.actorSystem) with ImplicitSender
                             with WordSpec with ClassicMatchers with BeforeAndAfterAll
                             with AsyncAssertions {

  implicit val executionContext = system.dispatcher

  implicit val timeout: akka.util.Timeout = 2 seconds
  val dummyJarsDir = new File("unicomplex/src/test/resources/classpaths")

  val port = Unicomplex.config getInt "bind-port"

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

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }
  }

  "UniComplex" must {

    "stop a single cube without affect other cubes" in {

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")

      val w = new Waiter

      val stopCubeFuture = Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")

      stopCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
      })

      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isFailure)}
        w.dismiss()
      })

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      w.await()

    }

    "not mess up if stop a stopped cube" in {
      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      val w = new Waiter

      val stopCubeFuture = Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")

      stopCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
      })

      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isFailure)}
        w.dismiss()
      })

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      w.await()
    }

    "start a single cube correctly" in {
      val w = new Waiter

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }

      val startCubeFuture = Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc")

      startCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
      })

      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if start a running cube" in {
      val w = new Waiter

      val startCubeFuture = Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc")

      startCubeFuture.onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get == Ack)
        }
        w.dismiss()
      })

      w.await()

      system.actorSelection("/user/DummyCubeSvc").resolveOne().onComplete(result => {
        w {assert(result.isSuccess)}
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if stop and start a cube contains actors and services simultaneously" in {
      val stopCubeFuture = Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")
      val startCubeFuture = Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc")

      val w = new Waiter
      Future.sequence(Seq(stopCubeFuture, startCubeFuture)).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if stop and start a cube contains actors only simultaneously" in {
      val stopCubeFuture = Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummyCube")
      val startCubeFuture = Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummyCube")

      val w = new Waiter
      Future.sequence(Seq(stopCubeFuture, startCubeFuture)).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if stop and start a cube contains services only simultaneously" in {
      val stopCubeFuture = Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummySvc")
      val startCubeFuture = Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummySvc")

      val w = new Waiter
      Future.sequence(Seq(stopCubeFuture, startCubeFuture)).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      w.await()

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "not mess up if stop all cubes simultaneously" in {
      val w = new Waiter

      Future.sequence(Seq(
        Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummySvc"),
        Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummyCube"),
        Unicomplex.uniActor ? StopCube("org.squbs.unicomplex.test.DummyCubeSvc")
      )).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString
      }

      intercept[FileNotFoundException]{
        Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString
      }
    }

    "not mess up if start all cubes simultaneously" in {
      val w = new Waiter

      Future.sequence(Seq(
        Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummySvc"),
        Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummyCube"),
        Unicomplex.uniActor ? StartCube("org.squbs.unicomplex.test.DummyCubeSvc")
      )).onComplete(result => {
        w {
          assert(result.isSuccess)
          assert(result.get.forall(_ == Ack))
        }
        w.dismiss()
      })

      assert(Source.fromURL(s"http://127.0.0.1:$port/dummysvc/msg/hello").mkString equals "^hello$")

      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/ping").mkString equals "Pong")
      assert(Source.fromURL(s"http://127.0.0.1:$port/pingpongsvc/pong").mkString equals "Ping")
    }

    "shutdown the system gracefully" in {

      future {(1 to 100) foreach {_ => Source.fromURL("http://127.0.0.1:9090/dummysvc/msg/hello")}}

      Unicomplex.uniActor ! GracefulStop

      awaitCond(system.isTerminated, 10 seconds, 2 seconds)
    }
  }
}

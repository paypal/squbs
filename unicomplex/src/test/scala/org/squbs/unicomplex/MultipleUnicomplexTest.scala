package org.squbs.unicomplex

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest._
import com.typesafe.config.ConfigFactory
import java.net.{InetSocketAddress, Socket}
import org.squbs._

/**
 * Created by junjshi on 14-6-13.
 */
object SystemStatusTest2 {

  val dummyJarsDir = "unicomplex/src/test/resources/classpaths"

  val classPaths = Array(
    "InitBlockCube",
    "InitCubeA",
    "InitCubeB",
    "InitFailCube"
  ) map (dummyJarsDir + "/" + _)

  import collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "SystemStatusTest2",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-service" -> Boolean.box(false),
      "default-listener.bind-port" -> nextPort.toString
    )
  )

  val mapConfig2 = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "SystemStatusTest3",
      "squbs." + JMX.prefixConfig -> Boolean.box(false),
      "default-listener.bind-service" -> Boolean.box(false),
      "default-listener.bind-port" -> nextPort.toString
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

  val boot2 = UnicomplexBoot(mapConfig2)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions.start()

}
class MultipleUnicomplexTest  extends TestKit(SystemStatusTest2.boot.actorSystem) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll
with SequentialNestedSuiteExecution{
  override def afterAll = {
    system.shutdown
    SystemStatusTest2.boot2.actorSystem.shutdown
  }

  "UniComplex" must {

    "get cube init reports" in {
      Unicomplex(system).uniActor ! ReportStatus
      val (systemState, cubes) = expectMsgType[(LifecycleState, Map[ActorRef, (CubeRegistration, Option[InitReports])])]
      systemState should be(Failed)
      val cubeAReport = cubes.values.find(_._1.name == "CubeA").flatMap(_._2)
      cubeAReport should not be (None)
      assert(cubeAReport.get.state == Active)
      val cubeBReport = cubes.values.find(_._1.name == "CubeB").flatMap(_._2)
      cubeBReport should not be (None)
      cubeBReport.get.state should be(Active)
      val initFailReport = cubes.values.find(_._1.name == "InitFail").flatMap(_._2)
      initFailReport should not be (None)
      initFailReport.get.state should be(Failed)
      val initBlockReport = cubes.values.find(_._1.name == "InitBlock").flatMap(_._2)
      initBlockReport should not be (None)
      initBlockReport.get.state should be(Initializing)

      val system2 = SystemStatusTest2.boot2.actorSystem
      Unicomplex(system2).uniActor ! ReportStatus
      val (systemState2, cubes2) = expectMsgType[(LifecycleState, Map[ActorRef, (CubeRegistration, Option[InitReports])])]
      systemState2 should be(Failed)
      val cubeAReport2 = cubes2.values.find(_._1.name == "CubeA").flatMap(_._2)
      cubeAReport2 should not be (None)
      assert(cubeAReport2.get.state == Active)
      val cubeBReport2 = cubes2.values.find(_._1.name == "CubeB").flatMap(_._2)
      cubeBReport2 should not be (None)
      cubeBReport2.get.state should be(Active)
      val initFailReport2 = cubes.values.find(_._1.name == "InitFail").flatMap(_._2)
      initFailReport2 should not be (None)
      initFailReport2.get.state should be(Failed)
      val initBlockReport2 = cubes.values.find(_._1.name == "InitBlock").flatMap(_._2)
      initBlockReport2 should not be (None)
      initBlockReport2.get.state should be(Initializing)



    }
  }
}




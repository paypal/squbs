/*
 *  Copyright 2017 PayPal
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

package org.squbs.cluster.test

import org.apache.pekko.actor.{ActorSelection, ActorSystem, PoisonPill, Terminated}
import org.apache.pekko.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.apache.curator.test.TestingServer
import org.squbs.cluster.ZkCluster
import org.squbs.cluster.test.ZkClusterMultiActorSystemTestKit._

import java.io.File
import java.net.{InetAddress, ServerSocket}
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Random, Success, Try}

abstract class ZkClusterMultiActorSystemTestKit(systemName: String)
  extends TestKit(ActorSystem(systemName, pekkoRemoteConfig)) with LazyLogging {

  val timeout: FiniteDuration

  val clusterSize: Int

  private var actorSystems = Map.empty[String, ActorSystem]

  def zkClusterExts: Map[String, ZkCluster] = actorSystems map { sys => sys._1 -> ZkCluster(sys._2)}

  def startCluster(): Unit = {
    val startTime = System.currentTimeMillis
    logger.info("Starting cluster of size {}", clusterSize)
    Random.setSeed(System.nanoTime)
    actorSystems = (0 until clusterSize) map { num =>
        val sysName: String = systemName(num)
        logger.info("Starting actor system {}", sysName)
        sysName -> ActorSystem(sysName, pekkoRemoteConfig withFallback zkConfig)
    } toMap

    // start the lazy actor
    zkClusterExts foreach { ext =>
      watch(ext._2.zkClusterActor)
    }

    Thread.sleep(500)
    logger.info("Finished starting cluster in {} ms", System.currentTimeMillis - startTime)
  }

  protected lazy val zkConfig = ConfigFactory.parseString(
    s"""
       |zkCluster {
       |  connectionString = "127.0.0.1:$ZOOKEEPER_DEFAULT_PORT"
       |  namespace = "zkclustersystest-${System.currentTimeMillis()}"
       |  segments = 1
       |}
    """.stripMargin)

  def shutdownCluster(): Unit = {
    logger.info("Shutting down cluster")
    zkClusterExts.foreach(ext => killSystem(ext._1))
    Await.ready(system.terminate(), timeout)
  }

  protected def systemName(num: Int): String = s"member-$num"

  implicit protected def zkCluster2Selection(zkCluster: ZkCluster): ActorSelection =
    system.actorSelection(zkCluster.zkClusterActor.path.toStringWithAddress(zkCluster.zkAddress))

  def killSystem(sysName: String): Unit = {
    zkClusterExts(sysName).addShutdownListener((_) => actorSystems(sysName).terminate())
    zkClusterExts(sysName).zkClusterActor ! PoisonPill
    expectMsgType[Terminated](timeout)
    actorSystems -= sysName
    logger.info("system {} got killed", sysName)
  }

  def bringUpSystem(sysName: String): Unit = {
    actorSystems += sysName -> ActorSystem(sysName, pekkoRemoteConfig withFallback zkConfig)
    watch(zkClusterExts(sysName).zkClusterActor)
    logger.info("system {} is up", sysName)
    Thread.sleep(timeout.toMillis / 5)
  }

  @tailrec final def pickASystemRandomly(exclude: Option[String] = None): String = {
    val candidate: String = systemName(Math.abs(Random.nextInt()) % clusterSize)
    (actorSystems get candidate, exclude) match {
      case (Some(sys), Some(ex)) if candidate != ex =>
        candidate
      case (Some(sys), None) =>
        candidate
      case _ => pickASystemRandomly(exclude)
    }
  }

}

object ZkClusterMultiActorSystemTestKit {

  val ZOOKEEPER_STARTUP_TIME = 5000
  val ZOOKEEPER_DEFAULT_PORT = 8085

  val zookeeperDir = new File("zookeeper")
  FileUtils.deleteQuietly(zookeeperDir)

  new TestingServer(ZOOKEEPER_DEFAULT_PORT, zookeeperDir, true)

  Thread.sleep(ZOOKEEPER_STARTUP_TIME)

  private def nextPort = {
    val s = new ServerSocket(0)
    val p = Try(s.getLocalPort) match {
      case Success(port) => port
      case Failure(e) => throw e
    }
    s.close()
    p
  }

  def pekkoRemoteConfig: Config = ConfigFactory.parseString(
    s"""
       |pekko {
       |  actor {
       |    provider = "org.apache.pekko.remote.RemoteActorRefProvider"
       |    serializers {
       |      kryo = "io.altoo.serialization.kryo.pekko.PekkoKryoSerializer"
       |    }
       |    serialization-bindings {
       |      "org.squbs.cluster.ZkMessages" = kryo
       |    }
       |  }
       |  remote {
       |    enabled-transports = ["pekko.remote.netty.tcp"]
       |    artery {
       |      transport = tcp # See Selecting a transport below
       |      canonical.hostname = ${InetAddress.getLocalHost.getHostAddress}
       |      canonical.port = $nextPort
       |    }
       |    log-received-messages = on
       |    log-sent-messages = on
       |    command-ack-timeout = 3 s
       |    retry-window = 1s
       |    gate-invalid-addresses-for = 1s
       |    transport-failure-detector {
       |      heartbeat-interval = 2s
       |      acceptable-heartbeat-pause = 5s
       |    }
       |    watch-failure-detector {
       |      heartbeat-interval = 2s
       |      acceptable-heartbeat-pause = 5s
       |      threshold = 10.0
       |    }
       |  }
       |}
     """.stripMargin)
}

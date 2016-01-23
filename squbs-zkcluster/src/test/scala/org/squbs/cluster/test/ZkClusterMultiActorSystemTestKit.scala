/*
 *  Copyright 2015 PayPal
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

import java.io.File
import java.net.{InetAddress, ServerSocket}

import akka.actor.{ActorSelection, ActorSystem, PoisonPill, Terminated}
import akka.testkit.TestKit
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.apache.curator.test.TestingServer
import org.squbs.cluster.ZkCluster
import ZkClusterMultiActorSystemTestKit._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Success, Try, Random}

abstract class ZkClusterMultiActorSystemTestKit(systemName: String)
  extends TestKit(ActorSystem(systemName, akkaRemoteConfig)) with LazyLogging {

  val timeout: FiniteDuration

  val clusterSize: Int

  private var actorSystems = Map.empty[String, ActorSystem]

  def zkClusterExts: Map[String, ZkCluster] = actorSystems map { sys => sys._1 -> ZkCluster(sys._2)}

  def startCluster(): Unit = {
    Random.setSeed(System.nanoTime)
    actorSystems = (0 until clusterSize) map {num =>
      val sysName: String = num
      sysName -> ActorSystem(sysName, akkaRemoteConfig withFallback zkConfig)
    } toMap
    
    // start the lazy actor
    zkClusterExts foreach { ext =>
      watch(ext._2.zkClusterActor)
    }
    
    Thread.sleep(timeout.toMillis / 10)
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
    zkClusterExts.foreach(ext => killSystem(ext._1))
    system.shutdown()
    Thread.sleep(timeout.toMillis / 10)
  }

  implicit protected def int2SystemName(num: Int): String = s"member-$num"

  implicit protected def zkCluster2Selection(zkCluster: ZkCluster): ActorSelection =
    system.actorSelection(zkCluster.zkClusterActor.path.toStringWithAddress(zkCluster.zkAddress))

  def killSystem(sysName: String): Unit = {
    zkClusterExts(sysName).addShutdownListener((_) => actorSystems(sysName).shutdown)
    zkClusterExts(sysName).zkClusterActor ! PoisonPill
    expectMsgType[Terminated](timeout)
    actorSystems -= sysName
    logger.info("system {} got killed", sysName)
  }

  def bringUpSystem(sysName: String): Unit = {
    actorSystems += sysName -> ActorSystem(sysName, akkaRemoteConfig withFallback zkConfig)
    watch(zkClusterExts(sysName).zkClusterActor)
    logger.info("system {} is up", sysName)
    Thread.sleep(timeout.toMillis / 5)
  }

  @tailrec final def pickASystemRandomly(exclude: Option[String] = None): String = {
    val candidate: String = Math.abs(Random.nextInt()) % clusterSize
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

  def akkaRemoteConfig: Config = ConfigFactory.parseString(
    s"""
       |akka {
       |  actor {
       |    provider = "akka.remote.RemoteActorRefProvider"
       |  }
       |  remote {
       |    enabled-transports = ["akka.remote.netty.tcp"]
       |    netty.tcp {
       |      port = $nextPort
       |      hostname = ${InetAddress.getLocalHost.getHostAddress}
       |      server-socket-worker-pool {
       |        pool-size-min = 2
       |        pool-size-max = 4
       |      }
       |      client-socket-worker-pool {
       |        pool-size-min = 2
       |        pool-size-max = 4
       |      }
       |      connection-timeout = 1 s
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
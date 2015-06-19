/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the AUTHORS file distributed with this work for
 * additional information regarding copyright ownership.
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.squbs.cluster

import java.net.ServerSocket

import akka.actor.{PoisonPill, Terminated, ActorSelection, ActorSystem}
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.apache.zookeeper.server.quorum.QuorumPeerMain

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Random

import org.squbs.cluster.ZkClusterMultiActorSystemTestKit._

abstract class ZkClusterMultiActorSystemTestKit(systemName: String)
  extends TestKit(ActorSystem(systemName, akkaRemoteConfig withFallback zkConfig)) {

  val timeout: FiniteDuration

  val clusterSize: Int

  private var actorSystems = Map.empty[String, ActorSystem]

  def zkClusterExts = actorSystems map { sys => sys._1 -> ZkCluster(sys._2)}

  def startCluster(): Unit = {
    Random.setSeed(System.nanoTime)
    actorSystems = (0 until clusterSize) map {num =>
      val sysName: String = num
      sysName -> ActorSystem(sysName, akkaRemoteConfig withFallback zkConfig)
    } toMap
    
    // start the lazy actor
    zkClusterExts foreach { ext => 
      watch(ext._2.zkClusterActor)
      ext._2.addShutdownListener(actorSystems(ext._1).shutdown)
    }
    
    Thread.sleep(timeout.toMillis / 10)
    println()
    println("*********************************** Cluster Started ***********************************")
  }

  def shutdownCluster(): Unit = {
    println("*********************************** Shutting Down the Cluster ***********************************")
    val exts = zkClusterExts
    val head = exts.head
    head._2.addShutdownListener(() => head._2.zkClientWithNs.delete.guaranteed.deletingChildrenIfNeeded.forPath(""))
    exts.tail.foreach(ext => killSystem(ext._1))
    killSystem(head._1)
    system.shutdown()
  }

  implicit protected def int2SystemName(num: Int): String = s"member-$num"

  implicit protected def zkCluster2Selection(zkCluster: ZkCluster): ActorSelection =
    system.actorSelection(zkCluster.zkClusterActor.path.toStringWithAddress(zkCluster.zkAddress))

  def killSystem(sysName: String): Unit = {
    zkClusterExts(sysName).zkClusterActor ! PoisonPill
    expectMsgType[Terminated](timeout)
    actorSystems -= sysName
    println(s"system $sysName got killed")
  }

  def bringUpSystem(sysName: String): Unit = {
    actorSystems += sysName -> ActorSystem(sysName, akkaRemoteConfig withFallback zkConfig)
    watch(zkClusterExts(sysName).zkClusterActor)
    zkClusterExts(sysName).addShutdownListener(actorSystems(sysName).shutdown)
    println(s"system $sysName is up")
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
  lazy val now = System.nanoTime

  (new Thread() {
    override def run = {
      QuorumPeerMain.main(Array[String](this.getClass.getClassLoader.getResource("zoo.cfg").getFile))
      println("Zookeeper started")
    }
  }).start

  Thread.sleep(5000)

  private def nextPort = {
    val s = new ServerSocket(0)
    try {
      s.getLocalPort
    }
    catch {
      case e:Throwable =>
        throw new Exception("Couldn't find an open port: %s".format(e.getMessage))
    }
    finally {
      s.close()
    }
  }

  def akkaRemoteConfig = ConfigFactory.parseString(
    s"""
       |akka {
       |  actor {
       |    provider = "akka.remote.RemoteActorRefProvider"
       |  }
       |  remote {
       |    enabled-transports = ["akka.remote.netty.tcp"]
       |    netty.tcp {
       |      port = $nextPort
       |      hostname = ${ConfigUtil.ipv4}
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

  lazy val zkConfig = ConfigFactory.parseString(
    s"""
      |zkCluster {
      |  connectionString = "127.0.0.1:8085"
      |  namespace = "zkclustersystest-$now"
      |  segments = 1
      |}
    """.stripMargin)
  
}
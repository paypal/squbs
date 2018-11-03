package org.squbs.cluster

import java.net.{InetAddress, ServerSocket}

import akka.actor.{PoisonPill, Terminated, ActorSelection, ActorSystem}
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.Random

/**
 * Created by zhuwang on 1/28/15.
 */
import org.squbs.cluster.ZkClusterMultiActorSystemTestKit._

abstract class ZkClusterMultiActorSystemTestKit(systemName: String)
  extends TestKit(ActorSystem(systemName, akkaRemoteConfig withFallback zkConfig)) {

  val timeout: FiniteDuration

  val clusterSize: Int

  private var actorSystems = Map.empty[String, ActorSystem]

  def zkClusterExts = actorSystems map { sys => sys._1 -> ZkCluster(sys._2)}

  def startCluster: Unit = {
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

  def shutdownCluster: Unit = {
    println("*********************************** Shutting Down the Cluster ***********************************")
    val exts = zkClusterExts
    val head = exts.head
    head._2.addShutdownListener(() => head._2.zkClientWithNs.delete.guaranteed.deletingChildrenIfNeeded.forPath(""))
    exts.tail.foreach(ext => killSystem(ext._1))
    killSystem(head._1)
    system.shutdown
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

  lazy val zkConfig = ConfigFactory.parseString(
    s"""
      |zkCluster {
      |  //connectionString = "phx5qa01c-fb23.stratus.phx.qa.ebay.com:8085,phx5qa01c-596c.stratus.phx.qa.ebay.com:8085,phx5qa01c-e59d.stratus.phx.qa.ebay.com:8085"
      |  connectionString = "stage2p2084.qa.paypal.com:2181"
      |  namespace = "zkclustersystest-$now"
      |  segments = 1
      |}
    """.stripMargin)
  
}
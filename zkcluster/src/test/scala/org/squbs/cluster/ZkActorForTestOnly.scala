package org.squbs.cluster

import java.io.File
import java.net.InetAddress

import akka.actor.Actor
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.apache.zookeeper.server.quorum.QuorumPeerMain
import org.slf4j.LoggerFactory
import org.squbs.unicomplex.Unicomplex

/**
 * Created by huzhou on 4/21/14.
 */
class ZkActorForTestOnly extends Actor {

  private val log = LoggerFactory.getLogger(this.getClass)

  override def preStart = {

    val outputDir = Unicomplex(context.system).externalConfigDir
    val members = Seq("localhost")
    val port = 2181

    System.setProperty("zookeeper.log.dir", outputDir);
    System.setProperty("Dzookeeper.root.logger", "info");

    val dataDir = new File(outputDir, "data")
    if(!dataDir.exists()){
      dataDir.mkdir()
    }
    val txlogDir = new File(outputDir, "txlog")
    if(!txlogDir.exists()){
      txlogDir.mkdir()
    }

    val id = myid(members)
    log.warn("[zk] me:{} and myid:{} vs members:{}", me, id, members)

    if(id == "0"){
      log.warn("[zk] i am excluded from the zookeeper ensemble")
    }
    else {
      Files.write(id, new File(dataDir, "myid"), Charsets.UTF_8)

      val cfg = zoo(members, dataDir.getAbsolutePath.replaceAll("\\\\","/"), txlogDir.getAbsolutePath.replaceAll("\\\\","/"), port = port)
      log.warn("[zk] zoo.cfg:{}", cfg)
      Files.write(cfg, new File(outputDir, "zoo.cfg"), Charsets.UTF_8)

      QuorumPeerMain.main(Array[String](new File(outputDir, "zoo.cfg").getAbsolutePath))
    }
  }

  def receive:Receive = {
    case _ =>
  }

  private[this] def me = InetAddress.getLocalHost

  def myid(members:Seq[String]) = {
    members match {
      case Seq("localhost") => "1"
      case _ => (members.indexWhere(InetAddress.getByName(_) == me) + 1).toString
    }
  }

  def zoo(members:Seq[String],
          dataDir:String,
          dataLogDir:String,
          tickTimeMs:Int = 1000,
          initLimit:Int = 20,
          syncLimit:Int = 10,
          port:Int = 2181,
          maxClientCnxns:Int = 1024):String = {

    val prefix =
      s"""tickTime=$tickTimeMs
        |initLimit=$initLimit
        |syncLimit=$syncLimit
        |dataDir=$dataDir
        |dataLogDir=$dataLogDir
        |clientPort=$port
        |snapCount=1000000
        |autopurge.snapRetainCount=3
        |maxClientCnxns=$maxClientCnxns
      """.stripMargin

    val zipped:Seq[(String, Int)] = members.zipWithIndex

    zipped.foldLeft(prefix){(result, elem) =>
      s"$result\nserver.${elem._2 + 1}=${elem._1}:2888:3888"
    }
  }
}

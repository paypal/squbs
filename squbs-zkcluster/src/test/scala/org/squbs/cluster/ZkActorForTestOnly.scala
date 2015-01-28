///*
// * Licensed to Typesafe under one or more contributor license agreements.
// * See the AUTHORS file distributed with this work for
// * additional information regarding copyright ownership.
// * This file is licensed to you under the Apache License, Version 2.0 (the
// * "License"); you may not use this file except in compliance
// * with the License.  You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing,
// * software distributed under the License is distributed on an
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// * KIND, either express or implied.  See the License for the
// * specific language governing permissions and limitations
// * under the License.
// */
//package org.squbs.cluster
//
//import java.io.File
//import java.net.InetAddress
//
//import com.google.common.base.Charsets
//import com.google.common.io.Files
//import com.typesafe.scalalogging.slf4j.LazyLogging
//import org.apache.zookeeper.server.ZooKeeperServerMain
//
//class ZkActorForTestOnly(val externalConfigDir:String) extends LazyLogging {
//
//  private val log = logger
//  private var zkThread:Thread = null
//
//  def startup(port:Int = 2181) = {
//
//    val outputDir = externalConfigDir
//    val members = Seq("localhost")
//
//    System.setProperty("zookeeper.log.dir", outputDir);
//    System.setProperty("Dzookeeper.root.logger", "info");
//
//    val dataDir = new File(outputDir, "data")
//    if(!dataDir.exists()){
//      dataDir.mkdir()
//    }
//    val txlogDir = new File(outputDir, "txlog")
//    if(!txlogDir.exists()){
//      txlogDir.mkdir()
//    }
//
//    val id = myid(members)
//    log.warn("[zk] me:{} and myid:{} vs members:{}", me, id, members)
//
//    if(id == "0"){
//      log.warn("[zk] i am excluded from the zookeeper ensemble")
//    }
//    else {
//      Files.write(id, new File(dataDir, "myid"), Charsets.UTF_8)
//
//      val cfg = zoo(members, dataDir.getAbsolutePath.replaceAll("\\\\", "/"), txlogDir.getAbsolutePath.replaceAll("\\\\", "/"), port = port)
//      log.warn("[zk] zoo.cfg:{}", cfg)
//      Files.write(cfg, new File(outputDir, "zoo.cfg"), Charsets.UTF_8)
//
//      zkThread = new Thread(new ThreadGroup("zkclustergroup"), new Runnable(){
//        override def run = {
//          try {
//            val initializeAndRun = classOf[ZooKeeperServerMain].getDeclaredMethod("initializeAndRun", classOf[Array[String]])
//            initializeAndRun.setAccessible(true)
//            initializeAndRun.invoke(new ZooKeeperServerMain, Array[String](new File(outputDir, "zoo.cfg").getAbsolutePath))
//          }
//          catch {
//            case _: InterruptedException =>
//              println("zkcluster mock interruped")
//            case e: Exception =>
//              println("zkcluster mock got other exceptions:")
//              e.printStackTrace
//          }
//        }
//      })
//      zkThread.setDaemon(true)
//      zkThread.start
//    }
//  }
//
//  def postStop = {
//    try {
//      zkThread.interrupt
//    }
//    catch{
//      case e:Exception => println(e)
//    }
//  }
//
//  private[this] def me = InetAddress.getLocalHost
//
//  def myid(members:Seq[String]) = {
//    members match {
//      case Seq("localhost") => "1"
//      case _ => (members.indexWhere(InetAddress.getByName(_) == me) + 1).toString
//    }
//  }
//
//  def zoo(members:Seq[String],
//          dataDir:String,
//          dataLogDir:String,
//          tickTimeMs:Int = 1000,
//          initLimit:Int = 20,
//          syncLimit:Int = 10,
//          port:Int = 2181,
//          maxClientCnxns:Int = 1024):String = {
//
//    val prefix =
//      s"""tickTime=$tickTimeMs
//        |initLimit=$initLimit
//        |syncLimit=$syncLimit
//        |dataDir=$dataDir
//        |dataLogDir=$dataLogDir
//        |clientPort=$port
//        |snapCount=1000000
//        |autopurge.snapRetainCount=3
//        |maxClientCnxns=$maxClientCnxns
//      """.stripMargin
//
//    val zipped:Seq[(String, Int)] = members.zipWithIndex
//
//    zipped.foldLeft(prefix){(result, elem) =>
//      s"$result\nserver.${elem._2 + 1}=${elem._1}:2888:3888"
//    }
//  }
//}

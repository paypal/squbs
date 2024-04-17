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

package org.squbs.cluster

import org.apache.pekko.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.CreateMode
import org.squbs.cluster.test.{ZkClusterMultiActorSystemTestKit, ZkClusterTestHelper}

import scala.language.implicitConversions

class ZkClusterInitTest extends ZkClusterMultiActorSystemTestKit("ZkClusterInitTest")
  with LazyLogging with ZkClusterTestHelper {

  val par1 = ByteString("myPar1")
  val par2 = ByteString("myPar2")
  val par3 = ByteString("myPar3")

  implicit val log = logger
  implicit def string2ByteArray(s: String): Array[Byte] = s.toCharArray map (c => c.toByte)
  implicit def byteArray2String(array: Array[Byte]): String = array.map(_.toChar).mkString

  override def beforeAll(): Unit = {
    // We preset the data in Zookeeper.
    val startTime = System.currentTimeMillis
    val zkClient = CuratorFrameworkFactory.newClient(
      zkConfig.getString("zkCluster.connectionString"),
      new ExponentialBackoffRetry(ZkCluster.DEFAULT_BASE_SLEEP_TIME_MS, ZkCluster.DEFAULT_MAX_RETRIES)
    )
    zkClient.start()
    zkClient.blockUntilConnected()
    implicit val zkClientWithNS = zkClient.usingNamespace(zkConfig.getString("zkCluster.namespace"))
    guarantee("/leader", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/members", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/segments", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee("/segments/segment-0", Some(Array[Byte]()), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par1)}", Some("myPar1"), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par1)}/servants", None, CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par1)}/$$size", Some(3), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par2)}", Some("myPar2"), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par2)}/servants", None, CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par2)}/$$size", Some(3), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par3)}", Some("myPar3"), CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par3)}/servants", None, CreateMode.PERSISTENT)
    guarantee(s"/segments/segment-0/${keyToPath(par3)}/$$size", Some(3), CreateMode.PERSISTENT)
    zkClient.close()

    // Then we start the cluster.
    startCluster()

    logger.info("beforeAll() took {} ms.", System.currentTimeMillis - startTime)
  }

  "ZkCluster" should "list the partitions" in {
    zkClusterExts foreach {
      case (_, ext) =>
        ext tell (ZkListPartitions(ext.zkAddress), self)
        expectMsgType[ZkPartitions](timeout)
    }
  }

  "ZkCluster" should "load persisted partition information and sync across the cluster" in {
    zkClusterExts foreach {
      case (_, ext) =>
        ext tell (ZkQueryPartition(par1), self)
        expectMsgType[ZkPartition](timeout).members should have size 3
    }
    zkClusterExts foreach {
      case (_, ext) =>
        ext tell (ZkQueryPartition(par2), self)
        expectMsgType[ZkPartition](timeout).members should have size 3
    }
    zkClusterExts foreach {
      case (_, ext) =>
        ext tell (ZkQueryPartition(par3), self)
        expectMsgType[ZkPartition](timeout).members should have size 3
    }
  }

  "ZkCluster" should "list all the members across the cluster" in {
    val members = zkClusterExts.map(_._2.zkAddress).toSet
    zkClusterExts foreach {
      case (_, ext) =>
        ext tell (ZkQueryMembership, self)
        expectMsgType[ZkMembership](timeout).members should be (members)
    }
  }
}

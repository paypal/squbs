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

package org.squbs

import java.net.{URLDecoder, URLEncoder}
import java.nio.ByteBuffer
import java.nio.charset.Charset

import akka.actor.{Address, AddressFromURIString}
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.{NoNodeException, NodeExistsException}

package object cluster {

  trait SegmentationLogic {
    val segmentsSize:Int
    def segmentation(partitionKey:ByteString) = s"segment-${Math.abs(partitionKey.hashCode()) % segmentsSize}"
    def partitionZkPath(partitionKey:ByteString) = s"/segments/${segmentation(partitionKey)}/${keyToPath(partitionKey)}"
    def sizeOfParZkPath(partitionKey:ByteString) = s"${partitionZkPath(partitionKey)}/$$size"
    def servantsOfParZkPath(partitionKey:ByteString) = s"${partitionZkPath(partitionKey)}/servants"
  }

  case class DefaultSegmentationLogic(segmentsSize:Int) extends SegmentationLogic

  def guarantee(path:String, data:Option[Array[Byte]], mode:CreateMode = CreateMode.EPHEMERAL)
               (implicit zkClient:CuratorFramework, logger:Logger):String = {
    try{
      data match {
        case None => zkClient.create.withMode(mode).forPath(path)
        case Some(bytes) => zkClient.create.withMode(mode).forPath(path, bytes)
      }
    }
    catch{
      case e: NodeExistsException =>
        if(data.nonEmpty && data.get.length > 0){
          zkClient.setData().forPath(path, data.get)
        }
        path
      case e: Throwable =>
        logger.info("leader znode creation failed due to %s\n", e)
        path
    }
  }

  def safelyDiscard(path:String, recursive: Boolean = true)(implicit zkClient: CuratorFramework):String = {
    import scala.collection.JavaConversions._
    try{
      if(recursive)
        zkClient.getChildren.forPath(path).foreach(child => safelyDiscard(s"$path/$child", recursive))
      zkClient.delete.forPath(path)
      path
    }
    catch{
      case e: NoNodeException =>
        path
      case e: Throwable =>
        path
    }
  }

//  private[cluster] def orderByAge(partitionKey:ByteString, members:Set[Address])
//      (implicit zkClient:CuratorFramework, zkSegmentationLogic:SegmentationLogic):Seq[Address] = {
//
//    if(members.isEmpty)
//      Seq.empty[Address]
//    else {
//      val zkPath = zkSegmentationLogic.partitionZkPath(partitionKey)
//      val servants:Seq[String] = try{
//        zkClient.getChildren.forPath(zkPath)
//      }
//      catch {
//        case e:Exception => Seq.empty[String]
//      }
//
//      val ages:Map[Address, Long] = servants.filterNot(_ == "$size").map(child => try{
//          AddressFromURIString.parse(pathToKey(child)) -> zkClient.checkExists.forPath(s"$zkPath/$child").getCtime
//        }
//        catch{
//          case e:Exception =>
//            AddressFromURIString.parse(pathToKey(child)) -> -1L
//        }).filterNot(_._2 == -1L).toMap
//      //this is to ensure that the partitions query result will always give members in the order of oldest to youngest
//      //this should make data sync easier, the newly on-board member should always consult with the 1st member in the
//      //query result to sync with.
//      members.toSeq.sortBy(ages.getOrElse(_, 0L))
//    }
//    members.toSeq
//  }

  def keyToPath(name:String):String = URLEncoder.encode(name, "utf-8")

  def pathToKey(name:String):String = URLDecoder.decode(name, "utf-8")

  private[cluster] val BYTES_OF_INT = Integer.SIZE / java.lang.Byte.SIZE

  implicit def intToBytes(integer:Int):Array[Byte] = {
    val buf = ByteBuffer.allocate(BYTES_OF_INT)
    buf.putInt(integer)
    buf.rewind
    buf.array()
  }

  val UTF_8 = Charset.forName("utf-8")

  implicit class ByteConversions(val bytes: Array[Byte]) extends AnyVal {

    def toAddress: Option[Address] =
      Option(bytes) flatMap (b => if (b.length <= 0) None else Some(AddressFromURIString(new String(b, UTF_8))))

    def toInt: Int = ByteBuffer.wrap(bytes).getInt

    def toUtf8: String = new String(bytes, UTF_8)

    def toByteString: ByteString = ByteString(bytes)

    def toAddressSet: Set[Address] = {
      try {
        new String(bytes, UTF_8).split("[,]").map(seg => AddressFromURIString(seg.trim)).toSet
      }catch {
        case _: Throwable => Set.empty
      }
    }
  }


  implicit def byteStringToUtf8(bs:ByteString):String = new String(bs.toArray, UTF_8)

  implicit def addressToBytes(address:Address):Array[Byte] = {
    address.toString.getBytes(UTF_8)
  }

  implicit def AddressSetToBytes(members: Set[Address]): Array[Byte] = {
    members.mkString(",").getBytes(UTF_8)
  }
}

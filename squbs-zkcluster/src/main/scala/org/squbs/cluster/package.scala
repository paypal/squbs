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

package org.squbs

import java.net.{URLDecoder, URLEncoder}
import java.nio.ByteBuffer
import java.nio.charset.Charset

import org.apache.pekko.actor.{Address, AddressFromURIString}
import org.apache.pekko.util.ByteString
import com.typesafe.scalalogging.Logger
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.NodeExistsException

import scala.language.implicitConversions
import scala.util.Try
import scala.util.control.NonFatal
import scala.jdk.CollectionConverters._

package object cluster {

  trait SegmentationLogic {
    val segmentsSize:Int
    def segmentation(partitionKey:ByteString): String = s"segment-${Math.abs(partitionKey.hashCode()) % segmentsSize}"
    def partitionZkPath(partitionKey:ByteString): String = s"/segments/${segmentation(partitionKey)}/${keyToPath(partitionKey)}"
    def sizeOfParZkPath(partitionKey:ByteString): String = s"${partitionZkPath(partitionKey)}/$$size"
    def servantsOfParZkPath(partitionKey:ByteString): String = s"${partitionZkPath(partitionKey)}/servants"
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
      case NonFatal(e) =>
        logger.info("leader znode creation failed due to %s\n", e)
        path
    }
  }

  def safelyDiscard(path:String, recursive: Boolean = true)(implicit zkClient: CuratorFramework): String = Try {
    if(recursive) zkClient.getChildren.forPath(path).asScala.foreach(child => safelyDiscard(s"$path/$child", recursive))
    zkClient.delete.forPath(path)
    path
  } getOrElse path

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

    def toAddressSet: Set[Address] = Try {
      new String(bytes, UTF_8).split("[,]").map(seg => AddressFromURIString(seg.trim)).toSet
    } getOrElse Set.empty
  }


  implicit def byteStringToUtf8(bs:ByteString):String = new String(bs.toArray, UTF_8)

  implicit def addressToBytes(address:Address):Array[Byte] = {
    address.toString.getBytes(UTF_8)
  }

  implicit def addressSetToBytes(members: Set[Address]): Array[Byte] = {
    members.mkString(",").getBytes(UTF_8)
  }
}

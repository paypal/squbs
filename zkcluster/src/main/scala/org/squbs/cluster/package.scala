package org.squbs

import java.net.{URLDecoder, URLEncoder, InetAddress}
import org.squbs.unicomplex.ConfigUtil
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.annotation.tailrec
import akka.util.ByteString
import akka.actor.{AddressFromURIString, Address}
import org.apache.zookeeper.CreateMode
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.KeeperException.{NoNodeException, NodeExistsException}
import com.typesafe.scalalogging.slf4j.Logger

/**
 * Created by huzhou on 6/9/14.
 */
package object cluster {

  import scala.collection.JavaConversions._

  trait RebalanceLogic {

    val spareLeader:Boolean

    /**
     * @return partitionsToMembers compensated when size in service is short compared with what's required
     */
    def compensate(partitionsToMembers:Map[ByteString, Set[Address]], members:Seq[Address], size:(ByteString => Int)):Map[ByteString, Set[Address]] = {

      partitionsToMembers.map(assign => {
        val partitionKey = assign._1
        val servants = assign._2
        val requires = size(partitionKey)

        servants.size match {
          case size:Int if size < requires => //shortage, must be compensated
            partitionKey -> (servants ++ members.filterNot(servants.contains(_)).take(requires - servants.size))
          case size:Int if size > requires => //overflow, reduce the servants
            partitionKey -> servants.take(requires)
          case _ =>
            assign
        }
      })
    }

    /**
     * @return partitionsToMembers rebalanced
     */
    def rebalance(partitionsToMembers:Map[ByteString, Set[Address]], members:Set[Address]):Map[ByteString, Set[Address]] = {

      val utilization = partitionsToMembers.foldLeft(Map.empty[Address, Seq[ByteString]]){(memoize, assign) =>
        assign._2.foldLeft(memoize){(memoize, member) =>
          memoize.updated(member, memoize.getOrElse(member, Seq.empty) :+ assign._1)
        }
      }

      val ordered = members.toSeq.sortWith((one, two) => utilization.getOrElse(one, Seq.empty).size < utilization.getOrElse(two, Seq.empty).size)

      @tailrec def rebalanceRecursively(partitionsToMembers:Map[ByteString, Set[Address]],
                                        utilization:Map[Address, Seq[ByteString]],
                                        ordered:Seq[Address]):Map[ByteString, Set[Address]] = {

        val overflows = utilization.getOrElse(ordered.last, Seq.empty)
        val underflow = utilization.getOrElse(ordered.head, Seq.empty)

        if (overflows.size - underflow.size > 1) {
          val move = overflows.head
          val updatedUtil = utilization.updated(ordered.last, overflows.tail).updated(ordered.head, underflow :+ move)
          var headOrdered = ordered.tail.takeWhile(next => updatedUtil.getOrElse(ordered.head, Seq.empty).size < updatedUtil.getOrElse(next, Seq.empty).size)
          headOrdered = (headOrdered :+ ordered.head) ++ ordered.tail.drop(headOrdered.size)
          var rearOrdered = headOrdered.takeWhile(next => updatedUtil.getOrElse(headOrdered.last, Seq.empty).size > updatedUtil.getOrElse(next, Seq.empty).size)
          rearOrdered = (rearOrdered :+ headOrdered.last) ++ headOrdered.drop(rearOrdered.size).dropRight(1)/*drop the headOrdered.last*/

          rebalanceRecursively(partitionsToMembers.updated(move, partitionsToMembers.getOrElse(move, Set.empty) + ordered.head - ordered.last), updatedUtil, rearOrdered)
        }
        else
          partitionsToMembers
      }

      rebalanceRecursively(partitionsToMembers, utilization, ordered)
    }
  }

  class DefaultRebalanceLogic(val spareLeader:Boolean) extends RebalanceLogic

  object DefaultRebalanceLogic {

    def apply(spareLeader:Boolean) = new DefaultRebalanceLogic(spareLeader)
  }

  trait SegmentationLogic {

    val segmentsSize:Int

    def segmentation(partitionKey:ByteString) = s"segment-${Math.abs(partitionKey.hashCode) % segmentsSize}"

    def partitionZkPath(partitionKey:ByteString) = s"/segments/${segmentation(partitionKey)}/${keyToPath(partitionKey)}"

    def sizeOfParZkPath(partitionKey:ByteString) = s"${partitionZkPath(partitionKey)}/$$size"
  }

  case class DefaultSegmentationLogic(segmentsSize:Int) extends SegmentationLogic

  def guarantee(path:String, data:Option[Array[Byte]], mode:CreateMode = CreateMode.EPHEMERAL)(implicit zkClient:CuratorFramework, logger:Logger):String = {
    try{
      data match {
        case None => zkClient.create.withMode(mode).forPath(path)
        case Some(bytes) => zkClient.create.withMode(mode).forPath(path, bytes)
      }
    }
    catch{
      case e: NodeExistsException => {
        if(data.nonEmpty && data.get.length > 0){
          zkClient.setData.forPath(path, data.get)
        }
        path
      }
      case e: Throwable => {
        logger.info("leader znode creation failed due to %s\n", e)
        path
      }
    }
  }

  def safelyDiscard(path:String, recursive:Boolean = true)(implicit zkClient:CuratorFramework):String = {
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

  private[cluster] def orderByAge(partitionKey:ByteString, members:Set[Address])(implicit zkClient:CuratorFramework, zkSegmentationLogic:SegmentationLogic):Seq[Address] = {

    if(members.isEmpty)
      Seq.empty[Address]
    else {
      val zkPath = zkSegmentationLogic.partitionZkPath(partitionKey)
      val ages:Map[Address, Long] = zkClient.getChildren.forPath(zkPath).filterNot(_ == "$size").map(child => try{
          AddressFromURIString.parse(pathToKey(child)) -> zkClient.checkExists.forPath(s"$zkPath/$child").getCtime
        }
        catch{
          case e:Exception =>
            AddressFromURIString.parse(pathToKey(child)) -> -1L
        }).filterNot(_._2 == -1L).toMap
      //this is to ensure that the partitions query result will always give members in the order of oldest to youngest
      //this should make data sync easier, the newly onboard member should always consult with the 1st member in the query result to sync with.
      members.toSeq.sortBy(ages.getOrElse(_, 0L))
    }
  }

  private[cluster] def myAddress = InetAddress.getLocalHost.getCanonicalHostName match {
    case "localhost" => ConfigUtil.ipv4
    case h:String => h
  }

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

  implicit def bytesToInt(bytes:Array[Byte]) = ByteBuffer.wrap(bytes).getInt

  implicit def bytesToUtf8(bytes:Array[Byte]):String = new String(bytes, UTF_8)

  implicit def byteStringToUtf8(bs:ByteString):String = new String(bs.toArray, UTF_8)

  implicit def addressToBytes(address:Address):Array[Byte] = {
    address.toString.getBytes(UTF_8)
  }

  implicit def bytesToAddress(bytes:Array[Byte]):Option[Address] = {
    bytes match {
      case null => None
      case _ if bytes.length == 0 => None
      case _ => {
        val uri = new String(bytes, UTF_8)
        Some(AddressFromURIString(uri))
      }
    }
  }

  implicit def bytesToByteString(bytes:Array[Byte]):ByteString = {
    ByteString(bytes)
  }
}

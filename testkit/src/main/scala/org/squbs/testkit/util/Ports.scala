package org.squbs.testkit.util

import java.net.{DatagramSocket, ServerSocket}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by huzhou on 6/26/14.
 */
object Ports {

  private[this] val unavailable = new ConcurrentHashMap[Int, Boolean]()
  private[this] val nextAttempt = new AtomicInteger(0)

  def available(lower:Int = 1000, upper:Int = 9999):Int = {

    lower + nextAttempt.getAndIncrement % (upper - lower) match {

      case verifying if !unavailable.containsKey(verifying) =>
        unavailable.put(verifying, true)
        //this logic is an from mina framework:
        //https://svn.apache.org/repos/asf/directory/sandbox/jvermillard/mina/java/org/apache/mina/util/AvailablePortFinder.java
        var serverSocket:Option[ServerSocket] = None
        var udpUseSocket:Option[DatagramSocket] = None
        try{
          serverSocket = Some(new ServerSocket(verifying))
          serverSocket.foreach(_.setReuseAddress(true))
          udpUseSocket = Some(new DatagramSocket(verifying))
          udpUseSocket.foreach(_.setReuseAddress(true))
          //success
          verifying
        }
        catch {
          case ex: Throwable => //failure, try next port
            available(lower, upper)
        }
        finally{
          try{
            udpUseSocket.foreach(_.close)
            serverSocket.foreach(_.close)
          }
          catch {
            case _: Throwable => //ignored
          }
        }
      //all ports in range are unavailable
      case _ if unavailable.size == upper - lower =>
        -1
      //further attempt with the next port
      case _ =>
        available(lower, upper)
    }
  }
}

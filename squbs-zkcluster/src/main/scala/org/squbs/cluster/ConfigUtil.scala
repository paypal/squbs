package org.squbs.cluster

import java.net.NetworkInterface
import scala.collection.mutable
/**
 * Created by zhuwang on 1/28/15.
 */
object ConfigUtil {
  def ipv4 = {
    val addresses = mutable.Set.empty[String]
    val enum = NetworkInterface.getNetworkInterfaces
    while (enum.hasMoreElements) {
      val addrs = enum.nextElement.getInetAddresses
      while (addrs.hasMoreElements) {
        addresses += addrs.nextElement.getHostAddress
      }
    }
    val pattern = "\\d+\\.\\d+\\.\\d+\\.\\d+".r
    val matched = addresses.filter({
      case pattern() => true
      case _ => false
    })
      .filter(_ != "127.0.0.1")
    matched.head
  }
}

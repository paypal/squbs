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

import java.net.NetworkInterface
import scala.collection.mutable

object ConfigUtil {
  def ipv4 = {
    val addresses = mutable.Set.empty[String]
    val enum = NetworkInterface.getNetworkInterfaces
    while (enum.hasMoreElements) {
      val address = enum.nextElement.getInetAddresses
      while (address.hasMoreElements) {
        addresses += address.nextElement.getHostAddress
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

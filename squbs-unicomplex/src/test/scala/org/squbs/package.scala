/*
 * Licensed to Typesafe under one or more contributor license agreements.
 * See the CONTRIBUTING file distributed with this work for
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
package org

import java.net.{InetSocketAddress, Socket}
import java.io.IOException
import scala.util.control.NonFatal

/**
 * copied from twitter util for testing purpose only.
 */
package object squbs {


  /**
   * A generator of random local [[java.net.InetSocketAddress]] objects with
   * ephemeral ports.
   */
  private[this] def localSocketOnPort(port: Int) =
    new InetSocketAddress(port)

  private[this] val ephemeralSocketAddress = localSocketOnPort(0)

  def apply() = nextAddress()

  def nextAddress(): InetSocketAddress =
    localSocketOnPort(nextPort())

  def nextPort(): Int = {

    val s = new Socket
    s.setReuseAddress(true)
    try {
      s.bind(ephemeralSocketAddress)
      s.getLocalPort
    }
    catch {
      case NonFatal(e) =>
        if (e.getClass == classOf[IOException] || e.getClass == classOf[IllegalArgumentException])
          throw new Exception("Couldn't find an open port: %s".format(e.getMessage))
        else
          throw e
    }
    finally {
      s.close()
    }
  }
}

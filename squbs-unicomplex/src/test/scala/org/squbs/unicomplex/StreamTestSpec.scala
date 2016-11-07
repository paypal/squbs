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

package org.squbs.unicomplex

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.event.Logging
import akka.io.IO
import akka.pattern._
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.streamSvc.ChunkedRequestHandler
import spray.can.Http
import spray.client.HttpDialog
import spray.client.pipelining._
import spray.http._
import spray.util._

import scala.util.Try

object StreamTestSpec {
  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "StreamCube",
    "StreamSvc"
  ) map (dummyJarsDir + "/" + _)

  val (_, port) = Utils.temporaryServerHostnameAndPort()

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = StreamTest
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = $port
    """.stripMargin
  )

  val boot = UnicomplexBoot(config)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions
    .start()
}

class StreamTestSpec extends TestKit(StreamTestSpec.boot.actorSystem) with ImplicitSender with WordSpecLike
    with Matchers with BeforeAndAfterAll with AsyncAssertions {

  import system.dispatcher

  implicit val timeout: akka.util.Timeout =
  Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse Timeouts.askTimeout

  val port = system.settings.config getInt "default-listener.bind-port"

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  val interface = "127.0.0.1"
  val connect = Http.Connect(interface, port)

  val hostConnector = Http.HostConnectorSetup(interface, port)
  val Http.HostConnectorInfo(connector, _) = IO(Http).ask(hostConnector).await
  val log = Logging(system, getClass)

  "UniComplex" must {

    "upload file with correct parts" in {

      // locate the path to akka-actor jar in the file system and turn it into a stream of BodyPart-s
      //val actor_jar_path = System.getProperty("java.class.path").split(java.io.File.pathSeparator).filter(
      // p => p.indexOf("akka-actor") != -1)(0)
      val actor_jar_path =
        StreamTestSpec.getClass.getResource("/classpaths/StreamSvc/akka-actor_2.10-2.3.2.jar1").getPath
      val actorFile = new java.io.File (actor_jar_path)
      println("stream file path:"+actor_jar_path)
      println("Exists:"+actorFile.exists())
      println("Can Read:"+actorFile.canRead)
      require(actorFile.exists() && actorFile.canRead)
      val fileLength = actorFile.length()
      log.debug (s"akka-actor file=$actorFile size=$fileLength")
      val chunks = HttpData (actorFile).toChunkStream(65000)
      val parts = chunks.zipWithIndex.flatMap {
        case (httpData, index) => Seq(BodyPart(HttpEntity(httpData), s"segment-$index"))
      } toSeq


      val multipartFormData = MultipartFormData ( parts)

      val uploadResult = HttpDialog(connector)
        .send(Post(uri = "/streamsvc/file-upload", content = multipartFormData))
        .end
        .await(timeout)

      log.debug(s"file-upload result: $uploadResult")

      ChunkedRequestHandler.byteCount should be (fileLength)
      ChunkedRequestHandler.chunkCount should be (parts.length)
    }
  }
}
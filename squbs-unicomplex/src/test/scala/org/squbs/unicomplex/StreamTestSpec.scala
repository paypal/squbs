/*
 * Copyright 2015 PayPal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.squbs.unicomplex

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.Timeouts._

import scala.concurrent.Await

object StreamTestSpec {
  val dummyJarsDir = getClass.getClassLoader.getResource("classpaths").getPath

  val classPaths = Array(
    "StreamCube",
    "StreamSvc"
  ) map (dummyJarsDir + "/" + _)

  val config = ConfigFactory.parseString(
    s"""
       |squbs {
       |  actorsystem-name = StreamTestSpec
       |  ${JMX.prefixConfig} = true
       |}
       |default-listener.bind-port = 0
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

  implicit val am = ActorMaterializer()
  import system.dispatcher

  val portBindings = Await.result((Unicomplex(system).uniActor ? PortBindings).mapTo[Map[String, Int]], awaitMax)
  val port = portBindings("default-listener")

  override def afterAll() {
    Unicomplex(system).uniActor ! GracefulStop
  }

  "UniComplex" must {

    "upload file with correct parts" in {

      val filePath =
        StreamTestSpec.getClass.getResource("/classpaths/StreamSvc/dummy.txt").getPath
      val file = new java.io.File(filePath)
      require(file.exists() && file.canRead)

      val chunkSize = 8192
      val responseF = Http().singleRequest(HttpRequest(HttpMethods.POST,
                                           uri = s"http://127.0.0.1:$port/streamsvc/file-upload",
                                           entity = HttpEntity(MediaTypes.`application/octet-stream`,
                                                               FileIO.fromPath(file.toPath, chunkSize))))

      val actualResponseEntity = Await.result(responseF flatMap extractEntityAsString, awaitMax)
      val expectedNumberOfChunks = Math.ceil(file.length.toDouble / chunkSize).toInt
      val expectedResponseEntity = s"Chunk Count: $expectedNumberOfChunks ByteCount: ${file.length}"
      actualResponseEntity should be (expectedResponseEntity)
    }
  }
}
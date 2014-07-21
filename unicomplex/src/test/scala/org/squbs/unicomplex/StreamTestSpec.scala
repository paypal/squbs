package org.squbs.unicomplex

import com.typesafe.config.ConfigFactory
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.concurrent.AsyncAssertions
import scala.util.Try
import java.util.concurrent.TimeUnit
import org.squbs._
import spray.httpx.marshalling._
import spray.client.HttpDialog
import scala.Some
import akka.pattern._
import scala.concurrent.duration._
import org.squbs.lifecycle.GracefulStop
import org.squbs.unicomplex.streamCube._
import spray.json._
import spray.client.pipelining._
import spray.http._
import spray.httpx.unmarshalling._
import spray.util._
import spray.can.Http
import akka.actor._
import akka.io.IO
import akka.event.Logging


/**
 * Created by junjshi on 14-7-18.
 */
object StreamTestSpec {
  val dummyJarsDir = "unicomplex/src/test/resources/classpaths"

  val classPaths = Array(
    "StreamCube",
    "StreamSvc"
  ) map (dummyJarsDir + "/" + _)

  import collection.JavaConversions._

  val mapConfig = ConfigFactory.parseMap(
    Map(
      "squbs.actorsystem-name"    -> "StreamTest",
      "squbs." + JMX.prefixConfig -> Boolean.box(true),
      "default-listener.bind-service" -> Boolean.box(true),
      "default-listener.bind-port" -> nextPort.toString
    )
  )

  val boot = UnicomplexBoot(mapConfig)
    .createUsing {(name, config) => ActorSystem(name, config)}
    .scanComponents(classPaths)
    .initExtensions
    .start()

}

class StreamTestSpec extends TestKit(StreamTestSpec.boot.actorSystem) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll
with AsyncAssertions {

  implicit val timeout: akka.util.Timeout =
    Try(System.getProperty("test.timeout").toLong) map { millis =>
      akka.util.Timeout(millis, TimeUnit.MILLISECONDS)
    } getOrElse (120 seconds)

  val port = system.settings.config getInt "default-listener.bind-port"

  implicit val executionContext = system.dispatcher

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
      //val actor_jar_path = System.getProperty("java.class.path").split(java.io.File.pathSeparator).filter(p => p.indexOf("akka-actor") != -1)(0)
      val actor_jar_path = StreamTestSpec.getClass.getResource("/classpaths/StreamSvc/akka-actor_2.10-2.3.2.jar").getPath
      val actorFile = new java.io.File (actor_jar_path)
      require(actorFile.exists() && actorFile.canRead)
      val fileLength = actorFile.length()
      log.debug (s"akka-actor file=$actorFile size=$fileLength")
      val chunks = HttpData (actorFile).toChunkStream(65000)
      val parts = chunks.zipWithIndex.flatMap { case (httpData, index) => Seq(BodyPart(HttpEntity(httpData), s"${streamCube.PARTNAME_SEGMENT}$index"))} toSeq


      val req = streamCube.Create ("/a/b/123", 10.days.fromNow.time.toMillis,
        Some(List(
          SetAttrs (Map("a1"->AttributeValue("v1"), "a2"->AttributeValue("v2")))
        )),
        Some(List(
          ModifyMetadata ("0", List(SetAttrs (Map("sa1"->AttributeValue("sv1"), "sa2"->AttributeValue("sv2")))), Some(false)),
          ModifyMetadata ("1", List(SetAttrs (Map("sa21"->AttributeValue("sv21"), "sa22"->AttributeValue("sv22")))), Some(false))
        )))

      log.debug(s"Submitting request ${req.toJson.prettyPrint}")

      val multipartFormData = MultipartFormData (BodyPart(marshalUnsafe(req), streamCube.PARTNAME_REQUEST) +: parts)
      val uploadResult = HttpDialog(connector)
        .send(Post(uri = "/streamsvc/file-upload", content = multipartFormData))
        .end
        .map(_.entity.as[streamCube.CreateResult] match {
        case Right (res) => res
        case Left (err) => throw new RuntimeException (err.toString)
      })
        .await(timeout)

      log.debug(s"file-upload result: $uploadResult")
      uploadResult.parts.size should be(parts.length)
      uploadResult.bytesReceived should be(fileLength)

    }
  }
}
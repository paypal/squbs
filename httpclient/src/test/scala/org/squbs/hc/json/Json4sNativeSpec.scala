package org.squbs.hc.json

import org.scalatest.{Matchers, FlatSpec}
import org.json4s._
import native.Serialization._

/**
 * Created by hakuang on 6/2/2014.
 */
class Json4sNativeSpec extends FlatSpec with Matchers{

  "NotTypeHints Example (case class)" should "have correct behaviour of read/write" in {
    import Json4sNativeNoTypeHintsProtocol._
    val playInfo = PlayerInfo("d", "k", 30)
    val jsonString = """{"firstName":"d","lastName":"k","age":30}"""
    write(playInfo) should be (jsonString)
    read[PlayerInfo](jsonString) should be (playInfo)
  }

  "NotTypeHints Example (case class contain the other case class)" should "have correct behaviour of read/write" in {
    import Json4sNativeNoTypeHintsProtocol._
    val name = Player("d", "k")
    val playInfo = PlayerInfo2(name, 30)
    val jsonString = """{"name":{"firstName":"d","lastName":"k"},"age":30}"""
    write(playInfo) should be (jsonString)
    read[PlayerInfo2](jsonString) should be (playInfo)
  }

  "ShortTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sNativeShortTypeHintsProtocolExample._
    val animals = Animals(Dog("pluto") :: Fish(1.2) :: Nil)
    val jsonString = """{"animals":[{"jsonClass":"Dog","name":"pluto"},{"jsonClass":"Fish","weight":1.2}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

  "FullTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sNativeFullTypeHintsProtocolExample._
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val jsonString = """{"animals":[{"jsonClass":"org.squbs.hc.json.Dog","name":"lucky"},{"jsonClass":"org.squbs.hc.json.Fish","weight":3.4}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

  "Custome Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sNativeCustomProtocolExample._
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val jsonString = """{"animals":[{"$type$":"org.squbs.hc.json.Dog","name":"lucky"},{"$type$":"org.squbs.hc.json.Fish","weight":3.4}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

}

object Json4sNativeShortTypeHintsProtocolExample extends Json4sNativeShortTypeHintsProtocol {
  override def hints: List[Class[_]] = List(classOf[Dog], classOf[Fish])
}

object Json4sNativeFullTypeHintsProtocolExample extends Json4sNativeFullTypeHintsProtocol {
  override def hints: List[Class[_]] = List(classOf[Dog], classOf[Fish])
}

object Json4sNativeCustomProtocolExample extends Json4sNativeCustomProtocol {
  override implicit def json4sFormats: Formats = new Formats {
    val dateFormat = DefaultFormats.lossless.dateFormat
    override val typeHints = FullTypeHints(classOf[Fish] :: classOf[Dog] :: Nil)
    override val typeHintFieldName = "$type$"
  }
}
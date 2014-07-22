package org.squbs.httpclient.json

import org.scalatest.{Matchers, FlatSpec}
import org.json4s._
import jackson.Serialization._

/**
 * Created by hakuang on 6/2/2014.
 */
class Json4sJacksonSpec extends FlatSpec with Matchers{

  "NotTypeHints Example (case class)" should "have correct behaviour of read/write" in {
    import Json4sJacksonNoTypeHintsProtocol._
    val playInfo = PlayerInfo("d", "k", 30)
    val jsonString = """{"firstName":"d","lastName":"k","age":30}"""
    write(playInfo) should be (jsonString)
    read[PlayerInfo](jsonString) should be (playInfo)
  }

  "NotTypeHints Example (case class contain the other case class)" should "have correct behaviour of read/write" in {
    import Json4sJacksonNoTypeHintsProtocol._
    val name = Player("d", "k")
    val playInfo = PlayerInfo2(name, 30)
    val jsonString = """{"name":{"firstName":"d","lastName":"k"},"age":30}"""
    write(playInfo) should be (jsonString)
    read[PlayerInfo2](jsonString) should be (playInfo)
  }

  "ShortTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sJacksonShortTypeHintsProtocolExample._
    val animals = Animals(Dog("pluto") :: Fish(1.2) :: Nil)
    val jsonString = """{"animals":[{"jsonClass":"Dog","name":"pluto"},{"jsonClass":"Fish","weight":1.2}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

  "FullTypeHints Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sJacksonFullTypeHintsProtocolExample._
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val jsonString = """{"animals":[{"jsonClass":"org.squbs.httpclient.json.Dog","name":"lucky"},{"jsonClass":"org.squbs.httpclient.json.Fish","weight":3.4}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

  "Custome Example (inheritance)" should "have correct behaviour of read/write" in {
    import Json4sJacksonCustomProtocolExample._
    val animals = Animals(Dog("lucky") :: Fish(3.4) :: Nil)
    val jsonString = """{"animals":[{"$type$":"org.squbs.httpclient.json.Dog","name":"lucky"},{"$type$":"org.squbs.httpclient.json.Fish","weight":3.4}]}"""
    write(animals) should be (jsonString)
    read[Animals](jsonString) should be (animals)
  }

}

object Json4sJacksonShortTypeHintsProtocolExample extends Json4sJacksonShortTypeHintsProtocol {
  override def hints: List[Class[_]] = List(classOf[Dog], classOf[Fish])
}

object Json4sJacksonFullTypeHintsProtocolExample extends Json4sJacksonFullTypeHintsProtocol {
  override def hints: List[Class[_]] = List(classOf[Dog], classOf[Fish])
}

object Json4sJacksonCustomProtocolExample extends Json4sJacksonCustomProtocol {
  override implicit def json4sJacksonFormats: Formats = new Formats {
    val dateFormat = DefaultFormats.lossless.dateFormat
    override val typeHints = FullTypeHints(classOf[Fish] :: classOf[Dog] :: Nil)
    override val typeHintFieldName = "$type$"
  }
}

case class Player(firstName: String, lastName: String)
case class PlayerInfo(firstName: String, lastName: String, age: Int)
case class PlayerInfo2(name: Player, age: Int)

trait Animal
case class Dog(name: String) extends Animal
case class Fish(weight: Double) extends Animal
case class Animals(animals: List[Animal])
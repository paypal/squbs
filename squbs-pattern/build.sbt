import de.johoop.findbugs4sbt.FindBugs._
import Versions._

name := "squbs-pattern"

testOptions in Test := Seq(Tests.Argument("-l", "org.squbs.testkit.tags.SlowTest"))

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-contrib" % akkaV intransitive(),
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test->*"
)

findbugsSettings

// findbugsExcludeFilters := Some(scala.xml.XML.loadFile (baseDirectory.value / "findbugsExclude.xml"))

org.scalastyle.sbt.ScalastylePlugin.Settings

// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

instrumentSettings
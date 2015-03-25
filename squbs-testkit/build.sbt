import de.johoop.findbugs4sbt.FindBugs._
import Versions._

name := "squbs-testkit"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "io.spray" %% "spray-client"  % sprayV % "test"
)

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

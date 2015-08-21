
import Versions._

name := "squbs-testkit"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "io.spray" %% "spray-client"  % sprayV % "test",
  "io.spray" %% "spray-testkit" % sprayV % "test",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % "0.4.0",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test->default"
)

org.scalastyle.sbt.ScalastylePlugin.Settings

instrumentSettings
scalaVersion := "2.10.3"

name := "unicomplex"

organization := "org.squbs"

version := "0.0.3-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.typesafe.akka" %% "akka-agent" % "2.2.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3" % "test",
  "io.spray" % "spray-can" % "1.2.0",
  "io.spray" % "spray-http" % "1.2.0",
  "io.spray" % "spray-routing" % "1.2.0",
  "io.spray" % "spray-testkit" % "1.2.0" % "test"
)
scalaVersion := "2.10.3"

name := "testkit"

organization := "org.squbs"

version := "0.0.3-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1",
  "com.typesafe.akka" %% "akka-actor" % "2.2.3",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.3"
)


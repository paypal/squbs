import de.johoop.findbugs4sbt.FindBugs._

name := "squbs-pattern"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "2.1.0" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
  "org.zeromq" % "jeromq" % "0.3.3",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "test"
)

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

instrumentSettings
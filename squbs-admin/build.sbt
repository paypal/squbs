import Versions._

name := "squbs-admin"

Revolver.settings

Test / javaOptions += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "org.json4s" %% "json4s-jackson" % json4sV
)

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

updateOptions := updateOptions.value.withCachedResolution(true)

Compile / run / mainClass := Some("org.squbs.unicomplex.Bootstrap")
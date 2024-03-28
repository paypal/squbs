import Versions._

name := "squbs-admin"

Revolver.settings

Test / javaOptions += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "org.apache.pekko" %% "pekko-actor" % pekkoV,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpV,
  "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "org.json4s" %% "json4s-jackson" % json4sV
)

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

updateOptions := updateOptions.value.withCachedResolution(true)

Compile / run / mainClass := Some("org.squbs.unicomplex.Bootstrap")
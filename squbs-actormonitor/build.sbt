import Versions._

name := "squbs-actormonitor"

Test / javaOptions += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "org.apache.pekko" %% "pekko-actor" % pekkoV,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test
)

updateOptions := updateOptions.value.withCachedResolution(true)

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/unicomplex")

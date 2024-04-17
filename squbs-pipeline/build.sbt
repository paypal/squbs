import Versions._

name := "squbs-pipeline"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "org.apache.pekko" %% "pekko-actor" % pekkoV,
  "org.apache.pekko" %% "pekko-stream" % pekkoV,
  "org.apache.pekko" %% "pekko-http-core" % pekkoHttpV % Provided,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test
)

//enablePlugins(de.johoop.testngplugin.TestNGPlugin)

Test / testOptions ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pipeline"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

updateOptions := updateOptions.value.withCachedResolution(true)
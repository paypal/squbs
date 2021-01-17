import Versions._

name := "squbs-pipeline"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpV % Provided,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test
)

enablePlugins(de.johoop.testngplugin.TestNGPlugin)

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pipeline"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

updateOptions := updateOptions.value.withCachedResolution(true)
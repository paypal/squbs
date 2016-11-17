import Versions._

name := "squbs-ext"

Revolver.settings

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core" % metricsV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-http-core" % akkaV % "provided",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "org.scalatest" %% "scalatest" % scalatestV % "test->*"
)
import Versions._

name := "squbs-ext"

Revolver.settings

javaOptions in Test += "-Xmx512m"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core" % metricsV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpV % "provided",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test->default",
  "org.scalatest" %% "scalatest" % scalatestV % "test->*"
)
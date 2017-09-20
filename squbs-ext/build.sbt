import Versions._

name := "squbs-ext"

Revolver.settings

javaOptions in Test += "-Xmx512m"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core" % metricsV,
  "com.typesafe.akka" %% "akka-stream" % akkaV % "optional",
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpV % "optional",
  "de.heikoseeberger" %% "akka-http-json4s" % heikoseebergerAkkaHttpJsonV % "optional",
  "de.heikoseeberger" %% "akka-http-jackson" % heikoseebergerAkkaHttpJsonV % "optional",
  "org.json4s" %% "json4s-native" % json4sV % "optional",
  "org.json4s" %% "json4s-jackson" % json4sV % "optional",
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonV % "optional",
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV % "optional",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4" % "optional",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV % "optional",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test",
  "junit" % "junit" % junitV % "test",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default",
  "org.scalatest" %% "scalatest" % scalatestV % "test->*"
)

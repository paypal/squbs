import Versions._

name := "squbs-ext"

Revolver.settings

javaOptions in Test += "-Xmx512m"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "io.dropwizard.metrics" % "metrics-core" % metricsV,
  "io.dropwizard.metrics" % "metrics-jmx" % metricsV,
  "com.typesafe.akka" %% "akka-stream" % akkaV % Optional,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpV % Optional,
  "de.heikoseeberger" %% "akka-http-json4s" % heikoseebergerAkkaHttpJsonV % Optional,
  "de.heikoseeberger" %% "akka-http-jackson" % heikoseebergerAkkaHttpJsonV % Optional,
  "org.json4s" %% "json4s-native" % json4sV % Optional,
  "org.json4s" %% "json4s-jackson" % json4sV % Optional,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonV % Optional,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV % Optional,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV % Optional,
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV % Optional,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % Test,
  "junit" % "junit" % junitV % Test,
  "org.testng" % "testng" % testngV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test
)

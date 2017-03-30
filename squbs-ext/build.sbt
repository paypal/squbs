import Versions._

name := "squbs-ext"

Revolver.settings

javaOptions in Test += "-Xmx512m"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.7.0",
  "io.dropwizard.metrics" % "metrics-core" % metricsV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpV % "provided",
  "de.heikoseeberger" %% "akka-http-json4s" % "1.11.0",
  "de.heikoseeberger" %% "akka-http-jackson" % "1.11.0",
  "org.json4s" %% "json4s-native" % "3.5.0",
  "org.json4s" %% "json4s-jackson" % "3.5.0",
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonV,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "junit" % "junit" % junitV % "test",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default",
  "org.scalatest" %% "scalatest" % scalatestV % "test->*"
)
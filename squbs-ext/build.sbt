import Versions._

name := "squbs-ext"

Revolver.settings

Test / javaOptions += "-Xmx512m"

Test / testOptions ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "io.dropwizard.metrics" % "metrics-core" % metricsV,
  "io.dropwizard.metrics" % "metrics-jmx" % metricsV,
  "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
  "org.apache.pekko" %% "pekko-stream" % pekkoV % Optional,
  "org.apache.pekko" %% "pekko-http-core" % pekkoHttpV % Optional,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpV % Optional,
  "com.github.pjfanning" %% "pekko-http-json4s" % pjfanningAkkaHttpJsonV % Optional,
  "com.github.pjfanning" %% "pekko-http-jackson" % pjfanningAkkaHttpJsonV % Optional,
  "org.json4s" %% "json4s-native" % json4sV % Optional,
  "org.json4s" %% "json4s-jackson" % json4sV % Optional,
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonV % Optional,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV % Optional,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV % Optional,
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV % Optional,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV % Test,
  "org.apache.pekko" %% "pekko-stream-testkit" % pekkoV % Test,
  "junit" % "junit" % junitV % Test,
  "org.testng" % "testng" % testngV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test
)

import Versions._

name := "squbs-pattern"

Test / testOptions ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.squbs.testkit.tags.SlowTest"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

Test / javaOptions ++= Seq("-Xmx512m", "-ea")

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "org.slf4j" % "slf4j-api" % slf4jV,
  "net.openhft" % "chronicle-queue" % chronicleQueueV % Optional,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "junit" % "junit" % junitV % Test,
  "org.apache.commons" % "commons-math3" % "3.6.1" % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  "com.wix" %% "accord-core" % accordV % Optional,
  "org.json4s" %% "json4s-jackson" % json4sV % Optional,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV % Optional,
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV % Optional,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test
) ++ pekkoDependencies

def pekkoDependencies = Seq(
  "org.apache.pekko" %% "pekko-actor" % pekkoV,
  "org.apache.pekko" %% "pekko-stream" % pekkoV,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV % Test,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpV % Optional,
  "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpV % Test,
  "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV % Test
)


// Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

updateOptions := updateOptions.value.withCachedResolution(true)

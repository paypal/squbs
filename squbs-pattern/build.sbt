import Versions._

name := "squbs-pattern"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.squbs.testkit.tags.SlowTest"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "net.openhft" % "chronicle-queue" % chronicleQueueV % "optional",
  "org.scalatest" %% "scalatest" % scalatestV % "test->*",
  "junit" % "junit" % junitV % "test",
  "org.apache.commons" % "commons-math3" % "3.3" % "test->*",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default",
  "com.wix" %% "accord-core" % accordV % "optional",
  "org.json4s" %% "json4s-jackson" % json4sV % "optional",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4" % "optional",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV % "optional",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
) ++ akkaDependencies

def akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.typesafe.akka" %% "akka-contrib" % akkaV % "optional",
  "com.typesafe.akka" %% "akka-http" % akkaHttpV % "optional",
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
)


// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

updateOptions := updateOptions.value.withCachedResolution(true)

import Versions._

name := "squbs-unicomplex"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % "test->*",
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test",
  "org.scala-lang.modules" %% "scala-java8-compat" % scalaJava8CompatV,
  "com.wix" %% "accord-core" % accordV % "test"
) ++ akka

def akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % "test"
)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

updateOptions := updateOptions.value.withCachedResolution(true)
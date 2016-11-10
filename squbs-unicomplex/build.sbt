import Versions._

name := "squbs-unicomplex"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "2.2.1" % "test->*",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.7.0",
  "com.wix" %% "accord-core" % "0.5"
) ++ akka(akkaV)

def akka(v: String) = Seq(
  "com.typesafe.akka" %% "akka-actor" % v,
  "com.typesafe.akka" %% "akka-agent" % v,
  "com.typesafe.akka" %% "akka-http-experimental" % v,
  "com.typesafe.akka" %% "akka-testkit" % v % "test",
  "com.typesafe.akka" %% "akka-stream-testkit" % v % "test"
)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

updateOptions := updateOptions.value.withCachedResolution(true)
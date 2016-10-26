import Versions._

name := "squbs-admin"

Revolver.settings

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % "test->*",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "io.spray" %% "spray-can"     % sprayV,
  "io.spray" %% "spray-http"    % sprayV,
  "io.spray" %% "spray-routing-shapeless2" % sprayV,
  "io.spray" %% "spray-testkit" % sprayV % "test",
  "io.spray" %% "spray-client"  % sprayV % "test",
  "io.spray" %% "spray-json"    % "1.3.2" % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test",
  "org.json4s" %% "json4s-jackson" % "3.3.0"
)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

updateOptions := updateOptions.value.withCachedResolution(true)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")
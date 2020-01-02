import Versions._

name := "squbs-admin"

Revolver.settings

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "org.json4s" %% "json4s-jackson" % json4sV,
  // This is added so that ScalaTest can produce an HTML report. Should be removed with scalatest 3.1.x
  "org.pegdown" % "pegdown" % pegdownV % Test
)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

updateOptions := updateOptions.value.withCachedResolution(true)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")
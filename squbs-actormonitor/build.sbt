import Versions._

name := "squbs-actormonitor"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test
)

updateOptions := updateOptions.value.withCachedResolution(true)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/unicomplex")

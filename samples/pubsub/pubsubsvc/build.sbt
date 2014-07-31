packSettings

atmosSettings

jacoco.settings

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings


scalaVersion := "2.10.3"

val akkaV = "2.2.3"

val sprayV = "1.2.0"

val squbsV = "0.0.3-SNAPSHOT"

traceAkka(akkaV)

dependencyOverrides ++= Set(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.5"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "io.spray" % "spray-routing" % sprayV,
  "org.squbs" %% "unicomplex" % squbsV,
  "com.ebay.squbs" %% "rocksqubs" % squbsV,
  "com.ebay.squbs" %% "rocksqubsperfmon" % squbsV,
  "com.ebay.squbs" %% "rocksqubsvi" % squbsV,
  "com.ebay.kernel" % "uKernelCore" % "9.0.2-squbs-SNAPSHOT",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.3.2",
  "org.scalatest" %% "scalatest" % "2.1.0" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "io.spray" % "spray-testkit" % sprayV % "test",
  "org.squbs" %% "testkit" % squbsV % "test"
)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")

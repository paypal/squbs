packSettings

atmosSettings

jacoco.settings

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings


scalaVersion := "2.10.4"

val akkaV = "2.3.2"

val sprayV = "1.3.1"

val squbsV = "0.5.0-SNAPSHOT"
val rocksqubsV = "0.5.0-SNAPSHOT"

dependencyOverrides ++= Set(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.5"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "io.spray" % "spray-routing" % sprayV,
  "org.squbs" %% "squbs-unicomplex" % squbsV,
  "com.ebay.squbs" %% "rocksqubs-kernel" % rocksqubsV,
  "com.ebay.squbs" %% "rocksqubs-perfmon" % rocksqubsV,
  "com.ebay.squbs" %% "rocksqubs-vi" % rocksqubsV,
  "com.ebay.kernel" % "uKernelCore" % "9.0.2-squbs-SNAPSHOT",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.3.2",
  "org.scalatest" %% "scalatest" % "2.1.0" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "io.spray" % "spray-testkit" % sprayV % "test",
  "org.squbs" %% "squbs-testkit" % squbsV % "test"
)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")

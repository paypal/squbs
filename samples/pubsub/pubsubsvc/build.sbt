findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

scalaVersion := "2.11.2"

val akkaV = "2.3.5"
val squbsV = "0.6.0-SNAPSHOT"
val rocksqubsV = "0.6.0-SNAPSHOT"

dependencyOverrides ++= Set(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.5"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "org.squbs" %% "squbs-unicomplex" % squbsV,
  "com.ebay.squbs" %% "rocksqubs-kernel" % squbsV,
  "com.ebay.squbs" %% "rocksqubs-perfmon" % squbsV,
  "com.ebay.squbs" %% "rocksqubs-vi" % squbsV,
  "com.ebay.kernel" % "uKernelCore" % "9.0.2-squbs-SNAPSHOT",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.3.2",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "org.squbs" %% "squbs-testkit" % squbsV % "test"
)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")

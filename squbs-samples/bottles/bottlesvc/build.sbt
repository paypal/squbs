scalaVersion := "2.10.3"

val akkaVersion = "2.2.3"

val sprayVersion = "1.2.0"

dependencyOverrides ++= Set(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.5"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "io.spray" % "spray-routing" % sprayVersion,
  "org.squbs" %% "unicomplex" % "0.0.2-SNAPSHOT",
  "com.ebay.squbs" %% "rocksqubs" % "0.0.2-SNAPSHOT",
  "com.ebay.raptor.core" % "ConfigWeb" % "2.0.0-RELEASE",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "io.spray" % "spray-testkit" % sprayVersion % "test",
  "org.squbs" %% "testkit" % "0.0.2-SNAPSHOT" % "test"
)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")

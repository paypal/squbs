scalaVersion := "2.10.4"

val akkaVersion = "2.3.2"
val squbsVersion = "0.5.0-SNAPSHOT"
val rocksqubsVersion = "0.5.0-SNAPSHOT"

dependencyOverrides += "org.slf4j" % "slf4j-api" % "1.7.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "org.squbs" %% "squbs-unicomplex" % squbsVersion,
  "com.ebay.squbs" %% "rocksqubs-kernel" % rocksqubsVersion,
  "com.ebay.raptor.core" % "ConfigWeb" % "2.0.0-RELEASE",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.squbs" %% "squbs-testkit" % squbsVersion % "test"
)

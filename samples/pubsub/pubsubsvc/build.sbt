findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

val akkaV = "2.4.20"
val squbsV = "0.9.2"

dependencyOverrides ++= Set(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.5"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "org.squbs" %% "squbs-unicomplex" % squbsV,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4",
  "org.scalatest" %% "scalatest" % scalatestV % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "org.squbs" %% "squbs-testkit" % squbsV % "test"
)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")

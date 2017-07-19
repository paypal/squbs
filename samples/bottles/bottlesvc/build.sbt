val akkaVersion = "2.4.16"
val squbsVersion = "0.9.1"
val scalatestV = "3.0.1"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "slf4j-jdk14" % "1.7.5",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "org.squbs" %% "squbs-unicomplex" % squbsVersion,
  "org.scalatest" %% "scalatest" % scalatestV % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.squbs" %% "squbs-testkit" % squbsVersion % "test"
)

mainClass in (Compile, run) := Some("org.squbs.unicomplex.Bootstrap")

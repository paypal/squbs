val akkaVersion = "2.3.6"
val SqubsVersion = "0.6.0-SNAPSHOT"
val rocksqubsVersion = "0.6.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "org.squbs" %% "squbs-unicomplex" % SqubsVersion,
  "com.ebay.squbs" %% "rocksqubs-kernel" % rocksqubsVersion,
  "com.ebay.raptor.core" % "ConfigWeb" % "2.1.7-RELEASE",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.squbs" %% "squbs-testkit" % SqubsVersion % "test"
)

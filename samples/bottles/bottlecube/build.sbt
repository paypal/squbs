val akkaVersion = "2.3.9"
val SqubsVersion = "0.7.1-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "org.squbs" %% "squbs-unicomplex" % SqubsVersion,
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.squbs" %% "squbs-testkit" % SqubsVersion % "test"
)

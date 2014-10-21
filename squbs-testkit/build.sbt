name := "squbs-testkit"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6",
  "io.spray" %% "spray-client"  % "1.3.2" % "test"
)

name := "testkit"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.1.0",
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.2",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0"
)

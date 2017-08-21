import Versions._

name := "squbs-actorregistry"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % "test->*",
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test",
  "junit" % "junit" % junitV % "test",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default"
)

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/unicomplex")
)

updateOptions := updateOptions.value.withCachedResolution(true)

javacOptions ++= Seq("-Xlint:unchecked")

scalacOptions ++= Seq("-feature")
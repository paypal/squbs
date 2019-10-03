import Versions._

name := "squbs-testkit"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % scalatestV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % Optional,
  "junit" % "junit" % junitV % Optional,
  "org.testng" % "testng" % testngV % Optional,
  "de.heikoseeberger" %% "akka-http-jackson" % heikoseebergerAkkaHttpJsonV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test

)

testOptions in Test += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")

javaOptions in Test += "-Xmx512m"

updateOptions := updateOptions.value.withCachedResolution(true)


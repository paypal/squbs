
import Versions._

name := "squbs-testkit"

resolvers += Resolver.sbtPluginRepo("releases")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % scalatestV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "provided",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % "0.7.0",
  "junit" % "junit" % junitV % "provided",
  "org.testng" % "testng" % testngV % "provided",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"

)

import de.johoop.testngplugin.TestNGPlugin._

testNGSettings

testOptions in Test += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")

javaOptions in Test += "-Xmx512m"

updateOptions := updateOptions.value.withCachedResolution(true)


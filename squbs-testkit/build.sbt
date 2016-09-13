
import Versions._

name := "squbs-testkit"

resolvers += Resolver.sbtPluginRepo("releases")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.1",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaV % "provided",
  "io.spray" %% "spray-client"  % sprayV % "test",
  "io.spray" %% "spray-testkit" % sprayV % "provided",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % "0.7.0",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test->default",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test",
  "org.testng" % "testng" % "6.8.8" % "test"

)

import de.johoop.testngplugin.TestNGPlugin._

testNGSettings

testOptions in Test += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")

javaOptions in Test += "-Xmx512m"

updateOptions := updateOptions.value.withCachedResolution(true)

// SimpleTestKitTest is causing actor system conflict with SimpleTestKitSpec.  Forking logic is specific to
// test name to minimize the duration of forked process (Travis CI limitations).
def groupBySimpleTestKitTest(tests: Seq[TestDefinition]) =
  tests groupBy (_.name == "org.squbs.testkit.japi.SimpleTestKitTest") map {
    case (letter, tests) => new Tests.Group(letter.toString, tests, Tests.SubProcess(ForkOptions()))
  } toSeq

testGrouping in Test := groupBySimpleTestKitTest((definedTests in Test).value)


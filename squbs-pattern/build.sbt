import Versions._
import spray.boilerplate.BoilerplatePlugin.Boilerplate

name := "squbs-pattern"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-l", "org.squbs.testkit.tags.SlowTest"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "net.openhft" % "chronicle-queue" % "4.3.2" % "provided",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test->*",
  "junit" % "junit" % "4.12" % "test",
  "org.apache.commons"         %  "commons-math3"       % "3.3"   % "test->*",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % "0.7.0" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test->default",
  "com.wix" %% "accord-core" % "0.5",
  "org.json4s"                %% "json4s-jackson"               % "3.3.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.6.3",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % "2.6.3",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
) ++ akkaDependencies(akkaV) ++ sprayDependencies(sprayV)

def akkaDependencies(v: String) = Seq(
  "com.typesafe.akka" %% "akka-actor" % v,
  "com.typesafe.akka" %% "akka-agent" % v,
  "com.typesafe.akka" %% "akka-stream" % v,
  "com.typesafe.akka" %% "akka-testkit" % v % "test",
  "com.typesafe.akka" %% "akka-contrib" % v intransitive(),
  "com.typesafe.akka" %% "akka-http-experimental" % v,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % v,
  "com.typesafe.akka" %% "akka-http-testkit" % v % "test"
)

def sprayDependencies(v: String) = Seq(
  "io.spray" %% "spray-http" % v,
  "io.spray" %% "spray-httpx" % v,
  "io.spray" %% "spray-routing-shapeless2" % v,
  "io.spray" %% "spray-json" % "1.3.2" % "test",
  "io.spray" %% "spray-testkit" % v % "test",
  "io.spray" %% "spray-httpx" % v % "test"
)

// : Seq[sbt.Def.Setting[_]] in the line below is not required for a successful build
// however, it is added due to an intelliJ warning
Boilerplate.settings : Seq[sbt.Def.Setting[_]]

// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

updateOptions := updateOptions.value.withCachedResolution(true)

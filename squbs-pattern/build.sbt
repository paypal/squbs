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
  "net.openhft" % "chronicle-queue" % "4.5.13" % "provided",
  "org.scalatest" %% "scalatest" % scalatestV % "test->*",
  "junit" % "junit" % "4.12" % "test",
  "org.apache.commons"         %  "commons-math3"       % "3.3"   % "test->*",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % "0.7.0" % "test",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default",
  "com.wix" %% "accord-core" % "0.5",
  "org.json4s"                %% "json4s-jackson"               % json4sV,
  "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.8.4",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonV,
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
) ++ akkaDependencies

def akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.typesafe.akka" %% "akka-contrib" % akkaV intransitive(),
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
)

// : Seq[sbt.Def.Setting[_]] in the line below is not required for a successful build
// however, it is added due to an intelliJ warning
Boilerplate.settings : Seq[sbt.Def.Setting[_]]

// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

updateOptions := updateOptions.value.withCachedResolution(true)

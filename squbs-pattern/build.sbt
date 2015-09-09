import Versions._
import spray.boilerplate.BoilerplatePlugin.Boilerplate

name := "squbs-pattern"

testOptions in Test := Seq(Tests.Argument("-l", "org.squbs.testkit.tags.SlowTest"))

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka"          %% "akka-agent"          % akkaV,
  "com.typesafe.akka" %% "akka-contrib" % akkaV intransitive(),
  "io.spray"          %% "spray-http" % sprayV,
  "io.spray"          %% "spray-httpx" % sprayV,
  "io.spray" %% "spray-routing" % sprayV,
  "io.spray" %% "spray-testkit" % sprayV % "test",
  "io.spray" %% "spray-httpx" % sprayV % "test",
  "io.spray" %% "spray-json" % sprayV % "test",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test->*",
  "junit" % "junit" % "4.12" % "test",
  "org.apache.commons"         %  "commons-math3"       % "3.3"   % "test->*",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % "0.4.0" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test->default",
  "com.wix" %% "accord-core" % "0.5"
)

// : Seq[sbt.Def.Setting[_]] in the line below is not required for a successful build
// however, it is added due to an intelliJ warning
Boilerplate.settings : Seq[sbt.Def.Setting[_]]

org.scalastyle.sbt.ScalastylePlugin.Settings

// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

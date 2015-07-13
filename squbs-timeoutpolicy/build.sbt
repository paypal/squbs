import Versions._

name := "squbs-timeoutpolicy"

libraryDependencies ++= Seq(
  "com.typesafe.akka"          %% "akka-agent"          % akkaV,
  "com.typesafe.scala-logging" %% "scala-logging"       % "3.1.0",
  "org.scalatest"              %% "scalatest"           % "2.2.1" % "test->*",
  "org.apache.commons"         %  "commons-math3"       % "3.3"   % "test->*",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % "0.4.0" % "test"
)

org.scalastyle.sbt.ScalastylePlugin.Settings

// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

instrumentSettings

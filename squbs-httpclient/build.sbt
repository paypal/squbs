import Versions._

name := "squbs-httpclient"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % akkaV,
  "com.typesafe.akka"         %% "akka-slf4j"                   % akkaV,
  "com.typesafe.akka"         %% "akka-testkit"                 % akkaV % "test",
  "com.typesafe.scala-logging" %% "scala-logging" 				% "3.1.0",
  "org.slf4j"                 %  "slf4j-api"                    % "1.7.5",
  "io.spray"                  %% "spray-client"                 % sprayV,
  "io.spray"                  %% "spray-routing"                % sprayV % "test",
  "io.spray"                  %% "spray-json"                   % "1.3.0" % "test",
  "org.scalatest"             %% "scalatest"                    % "2.2.1" % "test->*",
  "org.json4s"                %% "json4s-native"                % "3.2.9",
  "org.json4s"                %% "json4s-jackson"               % "3.2.9",
  "org.scala-lang.modules"    %% "scala-java8-compat"           % "0.4.0",
  "com.fasterxml.jackson.module"    %% "jackson-module-scala"           % "2.5.3"
)

org.scalastyle.sbt.ScalastylePlugin.Settings

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient")

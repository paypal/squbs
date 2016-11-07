import Versions._

name := "squbs-httpclient"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % akkaV,
  "com.typesafe.akka"         %% "akka-slf4j"                   % akkaV,
  "com.typesafe.akka"         %% "akka-testkit"                 % akkaV % "test",
  "com.typesafe.scala-logging" %% "scala-logging" 			      	% "3.1.0",
  "io.spray"                  %% "spray-client"                 % sprayV,
  "io.spray"                  %% "spray-routing-shapeless2"     % sprayV % "test",
  "io.spray"                  %% "spray-json"                   % "1.3.2" % "test",
  "org.scalatest"             %% "scalatest"                    % "2.2.1" % "test->*",
  "org.json4s"                %% "json4s-native"                % "3.3.0",
  "org.json4s"                %% "json4s-jackson"               % "3.3.0",
  "org.scala-lang.modules"    %% "scala-java8-compat"           % "0.7.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.6.3",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % "2.6.3",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)

javacOptions += "-parameters"

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient")

updateOptions := updateOptions.value.withCachedResolution(true)

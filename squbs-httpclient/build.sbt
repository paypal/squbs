import Versions._

name := "squbs-httpclient"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % akkaV,
  "com.typesafe.akka"         %% "akka-slf4j"                   % akkaV,
  "com.typesafe.akka"         %% "akka-http-core"               % akkaHttpV ,
  "com.typesafe.akka"         %% "akka-testkit"                 % akkaV % "test",
  "com.typesafe.scala-logging" %% "scala-logging" 			      	% "3.1.0",
  "de.heikoseeberger"         %% "akka-http-json4s"             % "1.11.0",
  "de.heikoseeberger"         %% "akka-http-jackson"            % "1.11.0",
  "org.scalatest"             %% "scalatest"                    % "3.0.0" % "test->*",
  "org.json4s"                %% "json4s-native"                % "3.5.0",
  "org.json4s"                %% "json4s-jackson"               % "3.5.0",
  "org.scala-lang.modules"    %% "scala-java8-compat"           % "0.7.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.8.4",
  "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % "2.8.5",
  "com.novocode" % "junit-interface" % "0.11" % "test->default",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)

javacOptions += "-parameters"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

updateOptions := updateOptions.value.withCachedResolution(true)

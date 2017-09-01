import Versions._

name := "squbs-httpclient"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % akkaV,
  "com.typesafe.akka"         %% "akka-slf4j"                   % akkaV,
  "com.typesafe.akka"         %% "akka-stream"                  % akkaV,
  "com.typesafe.akka"         %% "akka-http-core"               % akkaHttpV ,
  "com.typesafe.scala-logging" %% "scala-logging" 			      	% scalaLoggingV,
  "org.scalatest"             %% "scalatest"                    % scalatestV % "test->*",
  "com.typesafe.akka"         %% "akka-testkit"                 % akkaV % "test",
  "de.heikoseeberger" %% "akka-http-json4s" % heikoseebergerAkkaHttpJsonV % "test",
  "de.heikoseeberger" %% "akka-http-jackson" % heikoseebergerAkkaHttpJsonV % "test",
  "org.json4s" %% "json4s-jackson" % json4sV % "test",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4" % "test",
  "junit" % "junit" % junitV % "test",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)

javacOptions += "-parameters"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

updateOptions := updateOptions.value.withCachedResolution(true)

import Versions._

name := "squbs-httpclient"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % akkaV,
  "com.typesafe.akka"         %% "akka-slf4j"                   % akkaV,
  "com.typesafe.akka"         %% "akka-http-core"               % akkaHttpV ,
  "com.typesafe.scala-logging" %% "scala-logging" 			      	% "3.1.0",
  "org.scala-lang.modules"    %% "scala-java8-compat"           % "0.7.0",
  "org.scalatest"             %% "scalatest"                    % scalatestV % "test->*",
  "com.typesafe.akka"         %% "akka-testkit"                 % akkaV % "test",
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

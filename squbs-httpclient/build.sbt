import Versions._

name := "squbs-httpclient"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % akkaV,
  "com.typesafe.akka"         %% "akka-slf4j"                   % akkaV,
  "com.typesafe.akka"         %% "akka-stream"                  % akkaV,
  "com.typesafe.akka"         %% "akka-http-core"               % akkaHttpV ,
  "com.typesafe.scala-logging" %% "scala-logging"               % scalaLoggingV,
  "org.scalatest"             %% "scalatest"                    % scalatestV % Test,
  "com.typesafe.akka"         %% "akka-testkit"                 % akkaV % Test,
  "de.heikoseeberger" %% "akka-http-json4s" % heikoseebergerAkkaHttpJsonV % Test,
  "de.heikoseeberger" %% "akka-http-jackson" % heikoseebergerAkkaHttpJsonV % Test,
  "org.json4s" %% "json4s-jackson" % json4sV % Test,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV % Test,
  "junit" % "junit" % junitV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "org.littleshoot" % "littleproxy" % "1.1.2" % Test,
  // This is added so that ScalaTest can produce an HTML report. Should be removed with scalatest 3.1.x
  "org.pegdown" % "pegdown" % pegdownV % Test
)

javacOptions += "-parameters"

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

updateOptions := updateOptions.value.withCachedResolution(true)

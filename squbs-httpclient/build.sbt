import Versions._

name := "squbs-httpclient"

Test / javaOptions += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.apache.pekko"         %% "pekko-actor"                   % pekkoV,
  "org.apache.pekko"         %% "pekko-slf4j"                   % pekkoV,
  "org.apache.pekko"         %% "pekko-stream"                  % pekkoV,
  "org.apache.pekko"         %% "pekko-http-core"               % pekkoHttpV ,
  "com.typesafe.scala-logging" %% "scala-logging"               % scalaLoggingV,
  "org.scalatest"             %% "scalatest"                    % scalatestV % Test,
  "org.apache.pekko"         %% "pekko-testkit"                 % pekkoV % Test,
  "com.github.pjfanning" %% "pekko-http-json4s" % pjfanningAkkaHttpJsonV % Test,
  "com.github.pjfanning" %% "pekko-http-jackson" % pjfanningAkkaHttpJsonV % Test,
  "org.json4s" %% "json4s-jackson" % json4sV % Test,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonV % Test,
  "junit" % "junit" % junitV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "org.littleshoot" % "littleproxy" % "1.1.2" % Test
)

javacOptions += "-parameters"

Test / testOptions ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)

updateOptions := updateOptions.value.withCachedResolution(true)

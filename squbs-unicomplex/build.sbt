import Versions._

name := "squbs-unicomplex"

Test / javaOptions += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "org.scalatestplus" %% "mockito-3-4" % scalatestplusV % Test,
  "org.mockito" % "mockito-core" % mockitoV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "com.wix" %% "accord-core" % accordV % Test,
  "junit" % "junit" % junitV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test
) ++ pekkoDependencies

def pekkoDependencies = Seq(
  "org.apache.pekko" %% "pekko-actor" % pekkoV,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpV,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV % Test,
  "org.apache.pekko" %% "pekko-stream-testkit" % pekkoV % Test
)

Test / testOptions ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)


updateOptions := updateOptions.value.withCachedResolution(true)
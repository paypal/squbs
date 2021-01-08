import Versions._

name := "squbs-unicomplex"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "org.scalatestplus" %% "mockito-3-4" % scalatestplusV % Test,
  "org.mockito" % "mockito-core" % mockitoV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "com.wix" %% "accord-core" % accordV % Test,
  "junit" % "junit" % junitV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  // This is added so that ScalaTest can produce an HTML report. Should be removed with scalatest 3.1.x
  "org.pegdown" % "pegdown" % pegdownV % Test
) ++ akka

def akka = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % Test
)

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex"),
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
)


updateOptions := updateOptions.value.withCachedResolution(true)
import Versions._

name := "squbs-actorregistry"

javaOptions in Test += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-agent" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "junit" % "junit" % junitV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  // This is added so that ScalaTest can produce an HTML report. Should be removed with scalatest 3.1.x
  "org.pegdown" % "pegdown" % pegdownV % Test
)

testOptions in Test ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/unicomplex")
)

updateOptions := updateOptions.value.withCachedResolution(true)

javacOptions ++= Seq("-Xlint:unchecked")

scalacOptions ++= Seq("-feature")
import Versions._

name := "squbs-actorregistry"

Test / javaOptions += "-Xmx512m"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "org.apache.pekko" %% "pekko-actor" % pekkoV,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "junit" % "junit" % junitV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test
)

Test / testOptions ++= Seq(
  Tests.Argument(TestFrameworks.JUnit, "-v", "-a"),
  Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/unicomplex")
)

updateOptions := updateOptions.value.withCachedResolution(true)

javacOptions ++= Seq("-Xlint:unchecked")

scalacOptions ++= Seq("-feature")
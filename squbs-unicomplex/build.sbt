import de.johoop.findbugs4sbt.FindBugs._

name := "squbs-unicomplex"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "2.2.1" % "test->*",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.akka" %% "akka-agent" % "2.3.6",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.6",
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "ch.qos.logback" % "logback-core" % "1.0.11" % "runtime",
  "ch.qos.logback" % "logback-classic" % "1.0.11" % "runtime",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test",
  "io.spray" %% "spray-can"     % "1.3.2",
  "io.spray" %% "spray-http"    % "1.3.2",
  "io.spray" %% "spray-routing" % "1.3.2",
  "io.spray" %% "spray-testkit" % "1.3.2" % "test",
  "io.spray" %% "spray-client"  % "1.3.2" % "test",
  "io.spray" %% "spray-json"    % "1.3.0" % "test"
)

findbugsSettings

findbugsExcludeFilters := Some(scala.xml.XML.loadFile (baseDirectory.value / "findbugsExclude.xml"))

org.scalastyle.sbt.ScalastylePlugin.Settings

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

instrumentSettings
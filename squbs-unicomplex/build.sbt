import de.johoop.findbugs4sbt.FindBugs._

name := "squbs-unicomplex"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scalatest" %% "scalatest" % "2.2.1" % "test->*",
  "com.typesafe.akka" %% "akka-actor" % "2.3.5",
  "com.typesafe.akka" %% "akka-agent" % "2.3.5",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.5",
  "ch.qos.logback" % "logback-classic" % "1.0.0" % "runtime",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.5" % "test",
  "io.spray" %% "spray-can"     % "1.3.1",
  "io.spray" %% "spray-http"    % "1.3.1",
  "io.spray" %% "spray-routing" % "1.3.1",
  "io.spray" %% "spray-testkit" % "1.3.1" % "test",
  "io.spray" %% "spray-client"  % "1.3.1" % "test",
  "io.spray" %% "spray-json"    % "1.2.6" % "test"
)

findbugsSettings

findbugsExcludeFilters := Some(scala.xml.XML.loadFile (baseDirectory.value / "findbugsExclude.xml"))

org.scalastyle.sbt.ScalastylePlugin.Settings

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-unicomplex")

instrumentSettings

parallelExecution in ScoverageTest := false

parallelExecution in Test := false
import de.johoop.findbugs4sbt.FindBugs._
import Versions._

name := "squbs-timeoutpolicy"

libraryDependencies ++= Seq(
  "com.typesafe.akka"          %% "akka-agent"          % akkaV,
  "com.typesafe.scala-logging" %% "scala-logging"       % "3.1.0",
  "org.scalatest"              %% "scalatest"           % "2.2.1" % "test->*",
  "org.apache.commons"         %  "commons-math3"       % "3.3"   % "test->*"
)

findbugsSettings

// findbugsExcludeFilters := Some(scala.xml.XML.loadFile (baseDirectory.value / "findbugsExclude.xml"))

org.scalastyle.sbt.ScalastylePlugin.Settings

// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")

instrumentSettings

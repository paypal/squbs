import de.johoop.findbugs4sbt.FindBugs._
import Versions._

name := "squbs-timeoutpolicy"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-slf4j"           % akkaV,
  "com.typesafe.akka"         %% "akka-agent"           % akkaV,
  "org.slf4j"                 %  "slf4j-api"            % "1.7.5",
  "ch.qos.logback"			      %  "logback-core"					% "1.0.11" % "runtime",
  "ch.qos.logback" 			      %  "logback-classic" 			% "1.0.11" % "runtime",
  "org.scalatest"             %% "scalatest"            % "2.2.1" % "test->*",
  "org.apache.commons"        %  "commons-math3"         % "3.3"   % "test->*"
)

findbugsSettings

// findbugsExcludeFilters := Some(scala.xml.XML.loadFile (baseDirectory.value / "findbugsExclude.xml"))

org.scalastyle.sbt.ScalastylePlugin.Settings

// (testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-pattern")


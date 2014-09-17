import org.scalastyle.sbt.ScalastylePlugin._
import de.johoop.findbugs4sbt.FindBugs._

name := "squbs-httpclient"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % "2.3.5",
  "com.typesafe.akka"         %% "akka-slf4j"                   % "2.3.5",
  "com.typesafe.akka"         %% "akka-testkit"                 % "2.3.5" % "test",
  "com.typesafe.scala-logging" %% "scala-logging" 				% "3.1.0",
  "ch.qos.logback" 			  % "logback-classic" 				% "1.1.2" % "runtime",
  "io.spray"                  %% "spray-client"                 % "1.3.1",
  "io.spray"                  %% "spray-routing"                % "1.3.1" % "test",
  "io.spray"                  %% "spray-json"                   % "1.2.6" % "test",
  "org.scalatest"             %% "scalatest"                    % "2.2.1" % "test->*",
  "org.json4s"                %% "json4s-native"                % "3.2.9",
  "org.json4s"                %% "json4s-jackson"               % "3.2.9"
)

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient")

instrumentSettings

parallelExecution in ScoverageTest := false

parallelExecution in Test := false
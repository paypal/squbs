import org.scalastyle.sbt.ScalastylePlugin._
import de.johoop.findbugs4sbt.FindBugs._

name := "squbs-httpclient"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % "2.3.2",
  "com.typesafe.akka"         %% "akka-slf4j"                   % "2.3.2",
//  "org.slf4j"                 % "slf4j-simple"                  % "1.7.5",
  "ch.qos.logback"            % "logback-classic"               % "1.0.13",
  "com.typesafe.akka"         %% "akka-testkit"                 % "2.3.2" % "test",
  "io.spray"                  %% "spray-client"                 % "1.3.1",
  "io.spray"                  %% "spray-routing"                % "1.3.1" % "test",
  "io.spray"                  %% "spray-json"                   % "1.2.6" % "test",
  "org.scalatest"             %% "scalatest"                    % "2.1.0" % "test->*",
  "org.json4s"                %% "json4s-native"                % "3.2.9",
  "org.json4s"                %% "json4s-jackson"               % "3.2.9"
)

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

parallelExecution in Test := false

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/httpclient")

instrumentSettings

parallelExecution in ScoverageTest := false

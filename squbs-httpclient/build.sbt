import org.scalastyle.sbt.ScalastylePlugin._
import de.johoop.findbugs4sbt.FindBugs._
import Versions._

name := "squbs-httpclient"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % akkaV,
  "com.typesafe.akka"         %% "akka-testkit"                 % akkaV % "test",
  "io.spray"                  %% "spray-client"                 % sprayV,
  "io.spray"                  %% "spray-routing"                % sprayV % "test",
  "io.spray"                  %% "spray-json"                   % "1.3.0" % "test",
  "org.scalatest"             %% "scalatest"                    % "2.2.1" % "test->*",
  "org.json4s"                %% "json4s-native"                % "3.2.9",
  "org.json4s"                %% "json4s-jackson"               % "3.2.9"
)

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-httpclient")

instrumentSettings

import de.johoop.jacoco4sbt._
import JacocoPlugin._
import de.johoop.findbugs4sbt.FindBugs._

name := "httpclient"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor"                   % "2.3.2",
  "com.typesafe.akka"         %% "akka-slf4j"                   % "2.3.2",
  "io.spray"                  %  "spray-client"                 % "1.3.1",
  "org.scalatest"             %% "scalatest"                    % "2.1.0"  % "test",
  "org.json4s"                %% "json4s-native"                % "3.2.9",
  "org.json4s"                %% "json4s-jackson"               % "3.2.9"
)

jacoco.settings

findbugsSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

parallelExecution in Test := false

parallelExecution in jacoco.Config := false

//(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/httpclient")
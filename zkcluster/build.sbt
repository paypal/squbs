import org.scalastyle.sbt.ScalastylePlugin._
import de.johoop.findbugs4sbt.FindBugs._

name := "zkcluster"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "com.typesafe.akka" %% "akka-remote" % "2.3.2",
  "org.apache.curator" % "curator-recipes" % "2.4.1",
  "org.apache.curator" % "curator-framework" % "2.4.1",
  "org.apache.curator" % "curator-client" % "2.4.1" exclude("org.jboss.netty", "netty"),
  "org.apache.zookeeper" % "zookeeper" % "3.4.6",
  "com.typesafe" %% "scalalogging-slf4j" % "1.1.0",
  "com.google.protobuf" % "protobuf-java" % "2.5.0",
  "com.google.guava" % "guava" % "16.0.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
  "org.scalatest" %% "scalatest" % "2.1.0" % "test->*" exclude("org.jboss.netty", "netty"),
  "org.mockito" % "mockito-core" % "1.9.5" % "test",
  "log4j" % "log4j" % "1.2.17" % "test"
)

findbugsSettings

findbugsExcludeFilters := Some(scala.xml.XML.loadFile (baseDirectory.value / "findbugsExclude.xml"))

org.scalastyle.sbt.ScalastylePlugin.Settings

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/zkcluster")

instrumentSettings

parallelExecution in ScoverageTest := false
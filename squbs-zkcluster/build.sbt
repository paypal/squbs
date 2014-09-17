import org.scalastyle.sbt.ScalastylePlugin._
import de.johoop.findbugs4sbt.FindBugs._

name := "squbs-zkcluster"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.5",
  "com.typesafe.akka" %% "akka-remote" % "2.3.5",
  "org.apache.curator" % "curator-recipes" % "2.6.0",
  "org.apache.curator" % "curator-framework" % "2.6.0",
  "org.apache.curator" % "curator-client" % "2.6.0" exclude("org.jboss.netty", "netty"),
  "org.apache.zookeeper" % "zookeeper" % "3.4.6",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "log4j" % "log4j" % "1.2.17" % "test",
  "com.google.protobuf" % "protobuf-java" % "2.5.0",
  "com.google.guava" % "guava" % "16.0.1" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.5" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test->*" exclude("org.jboss.netty", "netty"),
  "org.mockito" % "mockito-core" % "1.9.5" % "test"
)

findbugsSettings

findbugsExcludeFilters := Some(scala.xml.XML.loadFile (baseDirectory.value / "findbugsExclude.xml"))

org.scalastyle.sbt.ScalastylePlugin.Settings

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-zkcluster")

instrumentSettings
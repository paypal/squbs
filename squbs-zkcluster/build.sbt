import Versions._

name := "squbs-zkcluster"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-remote" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "org.apache.curator" % "curator-recipes" % curatorV,
  "org.apache.curator" % "curator-framework" % curatorV exclude("org.jboss.netty", "netty"),
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "org.mockito" % "mockito-core" % mockitoV % Test,
  "org.apache.curator" % "curator-test" % curatorV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "commons-io" % "commons-io" % "2.6" % Test,
  // This is added so that ScalaTest can produce an HTML report. Should be removed with scalatest 3.1.x
  "org.pegdown" % "pegdown" % pegdownV % Test
)

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-zkcluster")

parallelExecution := false

cleanFiles += baseDirectory.value / "zookeeper"

updateOptions := updateOptions.value.withCachedResolution(true)

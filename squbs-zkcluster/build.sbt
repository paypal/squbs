import Versions._

name := "squbs-zkcluster"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-remote" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "org.apache.curator" % "curator-recipes" % curatorV,
  "org.apache.curator" % "curator-framework" % curatorV exclude("org.jboss.netty", "netty"),
  "io.altoo" %% "akka-kryo-serialization" % akkaKryoV,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % Test,
  "org.scalatest" %% "scalatest" % scalatestV % Test,
  "org.scalatestplus" %% "mockito-3-4" % scalatestplusV % Test,
  "org.mockito" % "mockito-core" % mockitoV % Test,
  "org.apache.curator" % "curator-test" % curatorV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test,
  "commons-io" % "commons-io" % "2.6" % Test
)

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "report/squbs-zkcluster")

parallelExecution := false

cleanFiles += baseDirectory.value / "zookeeper"

updateOptions := updateOptions.value.withCachedResolution(true)

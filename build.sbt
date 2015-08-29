

scalaVersion in ThisBuild := "2.11.6"

organization in ThisBuild := "org.squbs"

publishArtifact := false

org.scalastyle.sbt.ScalastylePlugin.Settings

coverageEnabled in ThisBuild := true

coverageMinimum in ThisBuild := 70.0

coverageFailOnMinimum in ThisBuild := true

fork in ThisBuild := true

parallelExecution in ThisBuild := false

updateOptions in ThisBuild := updateOptions.value.withCachedResolution(true)

lazy val `squbs-pipeline` = project

lazy val `squbs-unicomplex` = project dependsOn `squbs-pipeline`

lazy val `squbs-testkit` = project dependsOn `squbs-unicomplex`

lazy val `squbs-zkcluster` = project dependsOn `squbs-testkit` % "test"

lazy val `squbs-httpclient` = project dependsOn(`squbs-unicomplex`, `squbs-testkit` % "test")

// Add SlowTest configuration to squbs-pattern to run the long-running tests.
// To run standard tests> test
// To run slow tests including all stress tests> slow:test
lazy val SlowTest = config("slow") extend Test

// Setup squbs-pattern with slow tests enabled.
// Perhaps we can do it better in future by hiding the details in the plugin.
lazy val `squbs-pattern` = (project dependsOn `squbs-testkit` % "test")
  .configs(SlowTest)
  .settings(inConfig(SlowTest)(Defaults.testTasks): _*)
  .settings(testOptions in SlowTest := Seq.empty)

lazy val `squbs-actorregistry` = project dependsOn (`squbs-unicomplex`, `squbs-testkit` % "test")

lazy val `squbs-actormonitor` = project dependsOn (`squbs-unicomplex`, `squbs-testkit` % "test")

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

pomIncludeRepository in ThisBuild := { _ => false }

pomExtra in ThisBuild :=
  <url>https://github.com/paypal/squbs</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:paypal/squbs.git</url>
      <connection>scm:git:git@github.com:paypal/squbs.git</connection>
    </scm>
    <developers>
      <developer>
        <id>akara</id>
        <name>Akara Sucharitakul</name>
        <url>https://github.com/akara</url>
      </developer>
      <developer>
        <id>az-qbradley</id>
        <name>Qian Bradley</name>
        <url>https://github.com/az-qbradley</url>
      </developer>
      <developer>
        <id>anilgursel</id>
        <name>Anil Gursel</name>
        <url>https://github.com/anilgursel</url>
      </developer>
    </developers>

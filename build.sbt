import Shared._

crossScalaVersions in ThisBuild := Seq("2.11.8", "2.12.8")

organization in ThisBuild := "org.squbs"

publishArtifact := false

coverageEnabled in(Test, compile) := true

coverageEnabled in(Compile, compile) := false

coverageMinimum in ThisBuild := 70.0

coverageFailOnMinimum in ThisBuild := true

fork in ThisBuild := true

parallelExecution in ThisBuild := false

updateOptions in ThisBuild := updateOptions.value.withCachedResolution(true)

concurrentRestrictions in Global := Seq(Tags.limitAll(par))

lazy val `squbs-pipeline` = project

lazy val `squbs-unicomplex` = project dependsOn (`squbs-pipeline`, `squbs-ext`)

lazy val `squbs-testkit` = (project dependsOn `squbs-unicomplex`).enablePlugins(de.johoop.testngplugin.TestNGPlugin)

lazy val `squbs-zkcluster` = project dependsOn `squbs-testkit` % "test"

lazy val `squbs-httpclient` = project dependsOn(`squbs-ext` % "compile->compile;test->test",
  `squbs-pipeline`, `squbs-testkit` % "test")

// Add SlowTest configuration to squbs-pattern to run the long-running tests.
// To run standard tests> test
// To run slow tests including all stress tests> slow:test
lazy val SlowTest = config("slow") extend Test

// Setup squbs-pattern with slow tests enabled.
// Perhaps we can do it better in future by hiding the details in the plugin.
lazy val `squbs-pattern` = (project dependsOn (`squbs-ext`, `squbs-testkit` % "test"))
  .configs(SlowTest)
  .settings(inConfig(SlowTest)(Defaults.testTasks): _*)
  .settings(testOptions in SlowTest := Seq.empty)
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)

// Information for debugging tests and test launchers inside sbt.
// val DebugTest = config("dtest") extend Test
//
//  lazy val myProj = project
//  .configs(DebugTest).
//  settings(inConfig(DebugTest)(Defaults.testSettings):_*).
//  settings(
//    fork in DebugTest := true,
//    javaOptions in DebugTest += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
//    definedTests in DebugTest := (definedTests in Test).value
//  )

lazy val `squbs-actorregistry` = project dependsOn (`squbs-unicomplex`, `squbs-testkit` % "test")

lazy val `squbs-actormonitor` = project dependsOn (`squbs-unicomplex`, `squbs-testkit` % "test")

lazy val `squbs-admin` = project dependsOn (`squbs-unicomplex`, `squbs-testkit` % "test")

lazy val `squbs-ext` = project dependsOn `squbs-pipeline` % "provided"

publishTo in ThisBuild := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

// TODO: Remove the overwrite flag once https://github.com/sbt/sbt/issues/3725 is fixed.
import com.typesafe.sbt.pgp.PgpKeys.publishSignedConfiguration
publishSignedConfiguration := publishSignedConfiguration.value.withOverwrite(isSnapshot.value)

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
      <developer>
        <id>sebady</id>
        <name>Sherif Ebady</name>
        <url>https://github.com/sebady</url>
      </developer>
    </developers>

import ReleaseTransformations._

scalaVersion in ThisBuild := "2.11.6"

organization in ThisBuild := "org.squbs"

publishArtifact := false

addCommandAlias("coverage", "scoverage:test")

ScoverageKeys.minimumCoverage in ThisBuild := 70

ScoverageKeys.failOnMinimumCoverage in ThisBuild := true

fork in ThisBuild := true

parallelExecution in ScoverageTest in ThisBuild := false

parallelExecution in ThisBuild := false

updateOptions in ThisBuild := updateOptions.value.withCachedResolution(true)

lazy val `squbs-pipeline` = project

lazy val `squbs-unicomplex` = project dependsOn `squbs-pipeline`

lazy val `squbs-zkcluster` = project

lazy val `squbs-httpclient` = project dependsOn (`squbs-unicomplex`, `squbs-testkit` % "test")

lazy val `squbs-testkit` = project dependsOn `squbs-unicomplex`

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

lazy val `squbs-actorregistry` = project dependsOn `squbs-unicomplex`

lazy val `squbs-actormonitor` = project dependsOn `squbs-unicomplex`

lazy val `squbs-timeoutpolicy` = project

releaseTagName := s"RELEASE-${(version in ThisBuild).value}"

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  //runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
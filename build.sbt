scalaVersion in ThisBuild := "2.11.2"

version in ThisBuild := "0.6.0-SNAPSHOT"

organization in ThisBuild := "org.squbs"

publishArtifact := false

addCommandAlias("coverage", "scoverage:test")

ScoverageKeys.minimumCoverage in ThisBuild := 70

ScoverageKeys.failOnMinimumCoverage in ThisBuild := true

fork in Test in ThisBuild := true

parallelExecution in ScoverageTest in ThisBuild := false

parallelExecution in ThisBuild := false

updateOptions in ThisBuild := updateOptions.value.withConsolidatedResolution(true)

lazy val `squbs-unicomplex` = project

lazy val `squbs-zkcluster` = project dependsOn `squbs-unicomplex`

lazy val `squbs-httpclient` = project dependsOn (`squbs-unicomplex`, `squbs-testkit` % "test") 

lazy val `squbs-testkit` = project dependsOn `squbs-unicomplex`

lazy val `squbs-pattern` = project dependsOn `squbs-testkit` % "test"

lazy val `squbs-actorregistry` = project dependsOn `squbs-unicomplex`

lazy val `squbs-actormonitor` = project dependsOn `squbs-unicomplex`

lazy val `squbs-timeoutpolicy` = project

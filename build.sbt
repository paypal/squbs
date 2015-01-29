scalaVersion in ThisBuild := "2.10.4"

version in ThisBuild := "0.5.1-SNAPSHOT"

organization in ThisBuild := "org.squbs"

publishArtifact := false

addCommandAlias("coverage", "scoverage:test")

ScoverageKeys.minimumCoverage := 70

ScoverageKeys.failOnMinimumCoverage := true

parallelExecution in ScoverageTest := false

lazy val `squbs-unicomplex` = project

lazy val `squbs-zkcluster` = project

lazy val `squbs-httpclient` = project dependsOn (`squbs-unicomplex`, `squbs-testkit`)

lazy val `squbs-testkit` = project dependsOn `squbs-unicomplex`

lazy val `squbs-pattern` = project dependsOn (`squbs-unicomplex`, `squbs-testkit`)

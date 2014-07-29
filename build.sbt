scalaVersion in ThisBuild := "2.10.4"

version in ThisBuild := "0.6.0-SNAPSHOT"

organization in ThisBuild := "org.squbs"

publishArtifact := false

addCommandAlias("coverage", "scoverage:test")

ScoverageKeys.minimumCoverage := 70

ScoverageKeys.failOnMinimumCoverage := true

parallelExecution in ScoverageTest := false

lazy val unicomplex = project

lazy val zkcluster = project dependsOn unicomplex

lazy val httpclient = project dependsOn (unicomplex, testkit)

lazy val testkit = project dependsOn unicomplex

scalaVersion := "2.10.3"

version in ThisBuild := "0.0.9"

publishArtifact := false

lazy val unicomplex = project

lazy val testkit = project dependsOn unicomplex
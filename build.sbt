scalaVersion in ThisBuild := "2.10.3"

version in ThisBuild := "0.0.3-SNAPSHOT"

organization in ThisBuild := "org.squbs"

version in ThisBuild := "0.0.3-SNAPSHOT"

publishArtifact := false

lazy val unicomplex = project

lazy val testkit = project dependsOn unicomplex

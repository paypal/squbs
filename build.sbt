scalaVersion := "2.10.3"

publishArtifact := false

lazy val unicomplex = project

lazy val testkit = project dependsOn unicomplex

publishArtifact := false // Don't publish the root project.

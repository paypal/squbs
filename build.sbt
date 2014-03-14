import de.johoop.jacoco4sbt._
import JacocoPlugin._

scalaVersion in ThisBuild := "2.10.3"

version in ThisBuild := "0.0.3-SNAPSHOT"

organization in ThisBuild := "org.squbs"

publishArtifact := false

lazy val unicomplex = project

lazy val testkit = project dependsOn unicomplex

jacoco.settings

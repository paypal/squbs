name := "bottleSample"

organization in ThisBuild := "org.squbs.bottles"

scalaVersion in ThisBuild := "2.11.2"

version in ThisBuild := "0.0.2-SNAPSHOT"

publishArtifact := false

val bottlemsgs = project

val bottlecube = project dependsOn bottlemsgs

val bottlesvc = project dependsOn (bottlemsgs, bottlecube)

fork in ThisBuild := true

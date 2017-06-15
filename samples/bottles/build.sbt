name := "bottleSample"

organization in ThisBuild := "org.squbs.bottles"

scalaVersion in ThisBuild := "2.11.8"

version in ThisBuild := "0.10.0-SNAPSHOT"

publishArtifact := false

val bottlemsgs = project

val bottlecube = project dependsOn bottlemsgs

val bottlesvc = project dependsOn (bottlemsgs, bottlecube)

fork in ThisBuild := true

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)
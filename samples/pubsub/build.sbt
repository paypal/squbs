scalaVersion in ThisBuild := "2.11.7"

name := "pubsub"

organization in ThisBuild := "org.squbs.samples"

version in ThisBuild := "0.8.0"

publishArtifact := false

checksums in ThisBuild := Nil

fork in ThisBuild := true

lazy val pubsubsvc = project

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)
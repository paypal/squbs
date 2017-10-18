scalaVersion in ThisBuild := "2.11.8"

name := "pubsub"

organization in ThisBuild := "org.squbs.samples"

version in ThisBuild := "0.9.2"

publishArtifact := false

checksums in ThisBuild := Nil

fork in ThisBuild := true

lazy val pubsubsvc = project

resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)
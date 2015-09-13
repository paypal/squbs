name := "bottleSample"

organization in ThisBuild := "org.squbs.bottles"

scalaVersion in ThisBuild := "2.11.7"

version in ThisBuild := "0.7.0-SNAPSHOT"

publishArtifact := false

val bottlemsgs = project

val bottlecube = project dependsOn bottlemsgs

val bottlesvc = project dependsOn (bottlemsgs, bottlecube)

fork in ThisBuild := true

resolvers in ThisBuild ++= Seq(
  "eBay Central Releases" at "http://ebaycentral/content/repositories/releases/",
  "eBay Central Snapshots" at "http://ebaycentral/content/repositories/snapshots/",
  "Maven Central" at "http://ebaycentral/content/repositories/central/"
)
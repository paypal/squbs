scalaVersion := "2.11.2"

name := "pubsub"

organization in ThisBuild := "org.squbs.samples"

version in ThisBuild := "0.6.0-SNAPSHOT"

publishArtifact := false

checksums in ThisBuild := Nil

fork in ThisBuild := true

lazy val pubsubsvc = project 

// Metadata properties:
teamDL := "DL-eBay-PD-Scala@corp.ebay.com"

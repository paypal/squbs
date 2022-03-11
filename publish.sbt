ThisBuild / organization := "org.squbs"
ThisBuild / organizationName := "squbs.org"
ThisBuild / organizationHomepage := Some(url("https://github.com/paypal/squbs"))

ThisBuild / pomIncludeRepository := { _ => false }

ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

ThisBuild / publishMavenStyle := true

Test / publishArtifact := false
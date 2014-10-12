checksums in update := Nil

resolvers ++= Seq(
  "EBay Central Snapshots" at "http://ebaycentral/content/repositories/snapshots/"
)

addSbtPlugin("com.ebay.squbs" % "sbt-ebay" % "0.6.0-SNAPSHOT")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "0.99.7.1")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.4.0")

addSbtPlugin("de.johoop" % "findbugs4sbt" % "1.3.0")

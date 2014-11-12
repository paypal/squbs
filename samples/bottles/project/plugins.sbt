checksums in update := Nil

resolvers ++= Seq(
  "EBay Central Snapshots" at "http://ebaycentral/content/repositories/snapshots/"
)

addSbtPlugin("com.ebay.squbs" % "sbt-ebay" % "0.6.0-SNAPSHOT")

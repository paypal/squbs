checksums in update := Nil

resolvers ++= Seq(
 "eBay Central Releases" at "http://ebaycentral/content/repositories/releases/",
  "eBay Central Snapshots" at "http://ebaycentral/content/repositories/snapshots/",
  "Maven Central" at "http://ebaycentral/content/repositories/central/" 
)

addSbtPlugin("com.ebay.squbs" % "sbt-ebay" % "0.5.1-SNAPSHOT")

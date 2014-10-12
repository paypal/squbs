checksums in update := Nil

resolvers ++= Seq(
  "Raptor Snapshots" at "http://nxraptor/content/repositories/snapshots/"
)

addSbtPlugin("com.ebay.squbs" % "sbt-ebay" % "0.6.0-SNAPSHOT")

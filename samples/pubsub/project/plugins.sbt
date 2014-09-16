checksums in update := Nil

resolvers ++= Seq(
  "Raptor Snapshots" at "http://nxraptor/content/repositories/snapshots/"
)

addSbtPlugin("com.ebay.squbs" % "sbt-ebay" % "0.0.9-SNAPSHOT")

addSbtPlugin("de.johoop" % "jacoco4sbt" % "2.1.4")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.3.2")

addSbtPlugin("de.johoop" % "findbugs4sbt" % "1.2.2")

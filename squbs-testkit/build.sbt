import Versions._

name := "squbs-testkit"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-collection-compat" % scalaCompatV,
  "org.scalatest" %% "scalatest" % scalatestV,
  "org.scalatestplus" %% "testng-6-7" % scalatestplusV % Optional,
  "org.apache.pekko" %% "pekko-actor" % pekkoV,
  "org.apache.pekko" %% "pekko-testkit" % pekkoV,
  "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpV % Optional,
  "junit" % "junit" % junitV % Optional,
  "org.testng" % "testng" % testngV % Optional,
  "com.github.pjfanning" %% "pekko-http-jackson" % pjfanningAkkaHttpJsonV % Test,
  "com.novocode" % "junit-interface" % junitInterfaceV % Test,
  "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % Test,
  "ch.qos.logback" % "logback-classic" % logbackInTestV % Test

)

Test / testOptions += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")

Test / javaOptions += "-Xmx512m"

updateOptions := updateOptions.value.withCachedResolution(true)


import Versions._

name := "squbs-testkit"

resolvers += Resolver.sbtPluginRepo("releases")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % scalatestV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "optional",
  "org.scala-lang.modules"     %% "scala-java8-compat"  % scalaJava8CompatV,
  "junit" % "junit" % junitV % "optional",
  "org.testng" % "testng" % testngV % "optional",
  "de.heikoseeberger" %% "akka-http-jackson" % "1.11.0" % "test",
  "com.novocode" % "junit-interface" % junitInterfaceV % "test->default",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"

)

testOptions in Test += Tests.Argument(TestFrameworks.JUnit, "-v", "-a")

javaOptions in Test += "-Xmx512m"

updateOptions := updateOptions.value.withCachedResolution(true)


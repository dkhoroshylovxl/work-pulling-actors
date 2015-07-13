organization := """com.hunorkovacs"""

name := """work-pulling-actors"""

version := "1.0.0"

scalaVersion := "2.11.6"

libraryDependencies ++= List(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.specs2" %% "specs2-core" % "3.6.1" % "test"
)

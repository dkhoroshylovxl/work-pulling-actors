import SonatypeKeys._

sonatypeSettings

organization := """com.hunorkovacs"""

name := """work-pulling-actors"""

version := "1.0.0"

scalaVersion := "2.11.6"

libraryDependencies ++= List(
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.specs2" %% "specs2-core" % "3.6.1" % "test"
)

pomExtra := {
  <url>https://github.com/kovacshuni/work-pulling-actors</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/kovacshuni/work-pulling-actors</connection>
      <developerConnection>scm:git@github.com:kovacshuni/work-pulling-actors.git</developerConnection>
      <url>github.com/kovacshuni/work-pulling-actors</url>
      <tag>1.0.x</tag>
    </scm>
    <developers>
      <developer>
        <id>kovacshuni</id>
        <name>Hunor Kovács</name>
        <url>www.hunorkovacs.com</url>
      </developer>
    </developers>
}

publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

publishMavenStyle := true

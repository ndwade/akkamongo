name := "Iteratee Experiments"

version := "0.1"

scalaVersion := "2.9.2"

resolvers ++= Seq(
 "snapshots"             at "http://oss.sonatype.org/content/repositories/snapshots",
 "releases"              at "http://oss.sonatype.org/content/repositories/releases",
 "Typesafe Repository"   at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
 "com.typesafe.akka" % "akka-actor"    % "2.0.3",
 "com.typesafe.akka" % "akka-testkit"  % "2.0.3"  % "test",
 "org.specs2"        %% "specs2"       % "1.12.2" % "test"
)

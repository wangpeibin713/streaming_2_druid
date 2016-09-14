import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin._
import sbt._
import sbt.Keys._
assemblySettings

name := "streaming_to_druid"

version := "1.0"

scalaVersion := "2.10.6"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "com.google.protobuf" % "protobuf-java" % "2.5.0",
  "org.apache.spark"  %% "spark-core"             %  "1.6.1",
  "org.apache.spark"  %% "spark-sql"              %  "1.6.1",
  "org.apache.spark"  %% "spark-hive"             %   "1.6.1",
  "org.apache.spark"  %% "spark-streaming"        %  "1.6.1",
  "org.apache.spark"  %% "spark-streaming-kafka"  %  "1.6.1",
  "io.druid"           %% "tranquility-core" % "0.8.0",
  "io.druid"           %% "tranquility-spark" % "0.8.0",
  "org.postgresql" % "postgresql" % "9.4.1208.jre7",
  "io.dropwizard.metrics" % "metrics-graphite" % "3.1.2"
    exclude("org.slf4j", "slf4j-api")

)

mergeStrategy in assembly := {
  case PathList(ps @ _*) if ps.last endsWith ".SF" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".DSA" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".RSA" => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith "MANIFEST.MF" => MergeStrategy.discard
  case _  => MergeStrategy.first

}

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions ++= Seq("-encoding", "UTF-8", "-feature")
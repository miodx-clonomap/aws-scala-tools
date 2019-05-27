name := "aws-scala-tools"
organization := "com.miodx.common"
version := "0.20.0-SNAPSHOT"
description := "AWS Scala tools"

bucketSuffix := "era7.com"

crossScalaVersions := Seq("2.11.11", "2.12.8")
scalaVersion := "2.11.11"

val sdkVersion = "1.11.560"

val services = Seq(
  "autoscaling",
  "ec2",
  "s3",
  "sns",
  "sqs"
)

libraryDependencies ++= services.map { service =>
  "com.amazonaws" % s"aws-java-sdk-${service}" % sdkVersion
} ++ Seq(
  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)

wartremoverWarnings in (Compile, compile) --= Seq(
  Wart.Throw,
  Wart.DefaultArguments
)
wartremoverErrors in (Test, compile) --= Seq(
  Wart.TryPartial
)

parallelExecution in Test := false

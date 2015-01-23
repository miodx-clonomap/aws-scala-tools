Nice.scalaProject

name := "aws-scala-tools"

description := "AWS Scala tools"

organization := "ohnosequences"

bucketSuffix := "era7.com"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.11.4")

libraryDependencies ++= Seq(
//  "com.amazonaws" % "aws-java-sns" % "1.9.8",
//  "com.amazonaws" % "aws-java-sqs" % "1.9.8",
//  "com.amazonaws" % "aws-java-s3" % "1.9.8",
//  "com.amazonaws" % "aws-java-ec2" % "1.9.8",
//  "com.amazonaws" % "aws-java-iam" % "1.9.8",
  "com.amazonaws" % "aws-java-sdk" % "1.8.11",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

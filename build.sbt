ThisBuild / version      := "1.0.0"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "com.arkondata"

lazy val root = (project in file("."))
  .settings(
    name := "wifi-cdmx",

    libraryDependencies ++= Seq(

      // Akka HTTP (REST API)
      "com.typesafe.akka" %% "akka-http"            % "10.5.3",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream"           % "2.8.6",
      "com.typesafe.akka" %% "akka-actor-typed"      % "2.8.6",

      // Config & Logging
      "com.typesafe"               %  "config"          % "1.4.3",
      "ch.qos.logback"             %  "logback-classic" % "1.5.6",
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5",

      // Database
      "com.typesafe.slick" %% "slick"          % "3.5.1",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
      "org.postgresql"     %  "postgresql"     % "42.7.3",

      // Functional programming
      "org.typelevel" %% "cats-core" % "2.12.0",
    ),

    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-feature",
      "-deprecation",
      "-unchecked",
      "-language:higherKinds",
      "-language:implicitConversions"
    ),

    assembly / mainClass       := Some("com.arkondata.wificdmx.Main"),
    assembly / assemblyJarName := "wifi-cdmx.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF")  => MergeStrategy.discard
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case PathList("reference.conf")           => MergeStrategy.concat
      case PathList("application.conf")         => MergeStrategy.concat
      case _                                    => MergeStrategy.first
    }
  )
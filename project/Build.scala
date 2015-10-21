import sbt._
import sbt.Keys._
import bintray.BintrayKeys._

import scala.Some

object Build extends Build {

	import Dependencies._

	val scalaVer = "2.11.7"

	lazy val basicSettings = Seq(
		organization 				:= "co.blocke",
		startYear 					:= Some(2015),
		scalaVersion 				:= "2.11.7",
		version 					:= "0.1",
		parallelExecution in Test 	:= false,
		scalacOptions				:= Seq("-feature", "-deprecation", "-Xlint", "-encoding", "UTF8", "-unchecked", "-Xfatal-warnings"),
		testOptions in Test += Tests.Argument("-oDF")
	)

	// configure prompt to show current project
	override lazy val settings = super.settings :+ {
		shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
	}

	lazy val root = (project in file("."))
		.settings(basicSettings: _*)
		.settings(libraryDependencies ++=
			compile(scalajack,akka_rabbitmq,akka_actor,akka_streams) ++
			test(scalatest,akka_str_test,rabbit_http)
		)

	val pubSettings = Seq (
		publishMavenStyle := true,
		bintrayOrganization := Some("blocke"),
		bintrayReleaseOnPublish in ThisBuild := false,
		licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
		bintrayRepository := "releases",
		bintrayPackageLabels := Seq("scala", "rabbitmq")
	)
}

object Dependencies {
	def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
	def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test") 

	val akka_rabbitmq	= "com.thenewmotion.akka" 	%% "akka-rabbitmq" 	% "1.2.4"
	val akka_actor		= "com.typesafe.akka"		%% "akka-actor"		% "2.4.0"
	val akka_streams	= "com.typesafe.akka" 		%% "akka-stream-experimental"    % "1.0"
	val akka_str_test	= "com.typesafe.akka" 		%% "akka-stream-testkit-experimental"    % "1.0"
	val scalatest 		= "org.scalatest" 			%% "scalatest"		% "2.2.4"
	val scalajack		= "co.blocke"				%% "scalajack"		% "4.4.1"
	val rabbit_http     = "com.rabbitmq" 			% "http-client" 	% "1.0.0.RELEASE"
}

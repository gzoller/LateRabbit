import sbt._
import sbt.Keys._
// import bintray.BintrayKeys._

import scala.Some

object Build extends Build {

	import Dependencies._

	val scalaVer = "2.11.7"

	lazy val basicSettings = Seq(
		organization 				:= "co.blocke",
		startYear 					:= Some(2015),
		scalaVersion 				:= "2.11.7",
		resolvers					++= Dependencies.resolutionRepos,
		scalacOptions				:= Seq("-feature", "-deprecation", "-Xlint", "-encoding", "UTF8", "-unchecked", "-Xfatal-warnings"),
		testOptions in Test += Tests.Argument("-oDF")
	)

	// configure prompt to show current project
	override lazy val settings = super.settings :+ {
		shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
	}

	lazy val root = (project in file("."))
		.settings(basicSettings: _*)
		// .settings(pubSettings: _*)
		.settings(libraryDependencies ++=
			compile(scalajack,akka_rabbitmq,akka_actor,akka_streams) ++
			test(scalatest)
		)

	// val pubSettings = Seq (
	// 	publishMavenStyle := true,
	// 	bintrayOrganization := Some("blocke"),
	// 	bintrayReleaseOnPublish in ThisBuild := false,
	// 	licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
	// 	bintrayRepository := "releases",
	// 	bintrayPackageLabels := Seq("scala", "json", "scalajack")
	// )
}

object Dependencies {
	val resolutionRepos = Seq(
		// "Typesafe Repo"  		at "http://repo.typesafe.com/typesafe/releases/",
		// "Typesafe Snapshots"	at "http://repo.typesafe.com/typesafe/snapshots/",
		// "OSS"					at "http://oss.sonatype.org/content/repositories/releases",
		// "OSS Staging"			at "http://oss.sonatype.org/content/repositories/staging",
		// "PhantomMvn"			at "http://maven.websudos.co.uk/ext-release-local",
		// "Mvn" 					at "http://mvnrepository.com/artifact"  // for commons_exec
	)

	def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
	def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test") 

	val akka_rabbitmq	= "com.thenewmotion.akka" 	%% "akka-rabbitmq" 	% "1.2.4"
	val akka_actor		= "com.typesafe.akka"		%% "akka-actor"		% "2.4.0"
	val akka_streams	= "com.typesafe.akka" 		%% "akka-stream-experimental"    % "1.0"
	val typesafe_config	= "com.typesafe"			% "config"			% "1.2.1"
	val scalatest 		= "org.scalatest" 			%% "scalatest"		% "2.2.4"
	val scalajack		= "co.blocke"				%% "scalajack"		% "4.4.1"

	// val akka_slf4j 		= "com.typesafe.akka" 		%% "akka-slf4j"		% Akka
	// val akka_remote		= "com.typesafe.akka" 		%% "akka-remote"	% Akka
	// val akka_cluster	= "com.typesafe.akka" 		%% "akka-cluster" 	% Akka
	// val akka_contrib	= "com.typesafe.akka" 		%% "akka-contrib" 	% Akka
	// val akka_tools 		= "com.typesafe.akka"		%% "akka-cluster-tools" % Akka
	// val akka_str_test	= "com.typesafe.akka" 		%% "akka-stream-testkit-experimental"    % AkkaHttp
	// val akka_http_core	= "com.typesafe.akka" 		%% "akka-http-core-experimental" % AkkaHttp
	// val akka_http		= "com.typesafe.akka" 		%% "akka-http-experimental"      % AkkaHttp	
}

import uk.gov.hmrc.DefaultBuildSettings

Global / bloopAggregateSourceDependencies := true
Global / bloopExportJarClassifiers := Some(Set("sources"))
ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.7.4"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
lazy val microservice = Project("api-platform-organisation-frontend", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    retrieveManaged := true,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions ++= Seq(
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s", // TODO - remove once Play is really Scala 3
      "-Wconf:msg=unused&src=html/.*:s"
    ),
    Test / scalacOptions ++= Seq(
      "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s"
    ),
    routesImport ++= Seq(
      "uk.gov.hmrc.apiplatformorganisationfrontend.models.RouteModels._",
      "uk.gov.hmrc.apiplatformorganisationfrontend.models.RouteModels.given",
      "uk.gov.hmrc.apiplatform.modules.common.domain.models._",
      "uk.gov.hmrc.apiplatform.modules.organisations.domain.models._",
      "uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models._"
    )
  )
  .settings(
    Test / unmanagedSourceDirectories += baseDirectory.value / "test-utils",
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT")
  )
  .settings(
    TwirlKeys.templateImports ++= Seq(
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._",
      "uk.gov.hmrc.apiplatformorganisationfrontend.config.AppConfig",
      "uk.gov.hmrc.apiplatformorganisationfrontend.controllers"
    )
  )
  .settings(CodeCoverageSettings.settings: _*)

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(
    name := "integration-tests",
  )

commands ++= Seq(
  Command.command("cleanAll") { state => "clean" :: "it/clean" :: state },
  Command.command("fmtAll") { state => "scalafmtAll" :: "it/scalafmtAll" :: state },
  Command.command("fixAll") { state => "scalafixAll" :: "it/scalafixAll" :: state },
  Command.command("testAll") { state => "test" :: "it/test" :: state },

  Command.command("run-all-tests") { state => "testAll" :: state },
  Command.command("clean-and-test") { state => "cleanAll" :: "compile" :: "run-all-tests" :: state },
  Command.command("pre-commit") { state => "cleanAll" :: "fmtAll" :: "fixAll" :: "coverage" :: "testAll" :: "coverageOff" :: "coverageAggregate" :: state }
)
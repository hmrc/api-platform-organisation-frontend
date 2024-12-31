import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.5.0"
  private val tpdDomainVersion = "0.10.0"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-frontend-play-30" % bootstrapVersion,
    "uk.gov.hmrc" %% "play-frontend-hmrc-play-30" % "11.7.0"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30"       % bootstrapVersion % Test,
    "org.mockito" %% "mockito-scala-scalatest"      % "1.17.37"        % Test,
    "org.jsoup"    % "jsoup"                        % "1.18.3"         % Test,
    "uk.gov.hmrc" %% "api-platform-test-tpd-domain" % tpdDomainVersion % Test
  )

  val it = Seq.empty
}

import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.10.0"
  private val tpdDomainVersion = "0.13.0"
  private val orgDomainVersion = "0.4.0"

  val compile = Seq(
    "uk.gov.hmrc"      %% "bootstrap-frontend-play-30"       % bootstrapVersion,
    "uk.gov.hmrc"      %% "play-frontend-hmrc-play-30"       % "11.11.0",
    "uk.gov.hmrc"      %% "api-platform-tpd-domain"          % tpdDomainVersion,
    "uk.gov.hmrc"      %% "http-metrics"                     % "2.9.0",
    "uk.gov.hmrc"      %% "api-platform-organisation-domain" % orgDomainVersion,
    "commons-validator" % "commons-validator"                % "1.7"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30"                    % bootstrapVersion % Test,
    "org.mockito" %% "mockito-scala-scalatest"                   % "1.17.37"        % Test,
    "org.jsoup"    % "jsoup"                                     % "1.18.3"         % Test,
    "uk.gov.hmrc" %% "api-platform-organisation-domain-fixtures" % orgDomainVersion % Test,
    "uk.gov.hmrc" %% "api-platform-test-tpd-domain"              % tpdDomainVersion % Test
  )

  val it = Seq.empty
}

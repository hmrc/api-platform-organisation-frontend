import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.5.0"
  private val tpdDomainVersion = "0.14.0"
  private val orgDomainVersion = "0.12.0"
  private val appDomainVersion = "0.95.0"

  val compile = Seq(
    "uk.gov.hmrc"      %% "bootstrap-frontend-play-30"       % bootstrapVersion,
    "uk.gov.hmrc"      %% "play-frontend-hmrc-play-30"       % "12.31.0",
    "uk.gov.hmrc"      %% "api-platform-tpd-domain"          % tpdDomainVersion,
    "uk.gov.hmrc"      %% "http-metrics"                     % "2.9.0",
    "uk.gov.hmrc"      %% "api-platform-organisation-domain" % orgDomainVersion,
    "commons-validator" % "commons-validator"                % "1.7",
    "uk.gov.hmrc"      %% "api-platform-application-domain"  % appDomainVersion
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30"                    % bootstrapVersion % Test,
    "org.mockito" %% "mockito-scala-scalatest"                   % "1.17.37"        % Test,
    "org.jsoup"    % "jsoup"                                     % "1.18.3"         % Test,
    "uk.gov.hmrc" %% "api-platform-organisation-domain-fixtures" % orgDomainVersion % Test,
    "uk.gov.hmrc" %% "api-platform-test-tpd-domain"              % tpdDomainVersion % Test,
    "uk.gov.hmrc" %% "api-platform-application-domain-fixtures"  % appDomainVersion % Test
  )

  val it = Seq.empty
}

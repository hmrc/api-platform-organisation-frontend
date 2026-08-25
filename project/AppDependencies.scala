import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.8.0"
  private val commonDomainVersion = "1.4.0"
  private val tpdDomainVersion = "1.3.0"
  private val orgDomainVersion = "1.11.0"
  private val appDomainVersion = "1.6.0"

  val compile = Seq(
    "uk.gov.hmrc"      %% "bootstrap-frontend-play-30"       % bootstrapVersion,
    "uk.gov.hmrc"      %% "play-frontend-hmrc-play-30"       % "13.11.0",
    "uk.gov.hmrc"      %% "api-platform-common-domain"       % commonDomainVersion,
    "uk.gov.hmrc"      %% "api-platform-tpd-domain"          % tpdDomainVersion,
    "uk.gov.hmrc"      %% "api-platform-organisation-domain" % orgDomainVersion,
    "uk.gov.hmrc"      %% "api-platform-application-domain"  % appDomainVersion
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30"                    % bootstrapVersion,
    "org.jsoup"    % "jsoup"                                     % "1.22.1",
    "uk.gov.hmrc" %% "api-platform-common-domain-fixtures"       % commonDomainVersion,
    "uk.gov.hmrc" %% "api-platform-organisation-domain-fixtures" % orgDomainVersion,
    "uk.gov.hmrc" %% "api-platform-tpd-domain-fixtures"          % tpdDomainVersion,
    "uk.gov.hmrc" %% "api-platform-application-domain-fixtures"  % appDomainVersion
  ).map(_ % Test)

  val it = Seq.empty
}

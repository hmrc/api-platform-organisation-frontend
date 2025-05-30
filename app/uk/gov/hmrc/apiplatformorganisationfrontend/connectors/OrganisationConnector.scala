/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apiplatformorganisationfrontend.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.http.metrics.common.API

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Organisation, OrganisationId, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services._

@Singleton
class OrganisationConnector @Inject() (
    http: HttpClientV2,
    config: OrganisationConnector.Config,
    val metrics: ConnectorMetrics
  )(implicit ec: ExecutionContext
  ) {

  import OrganisationConnector._
  import Submission._

  val api = API("api-platfrom-organisation")

  def recordAnswer(submissionId: SubmissionId, questionId: Question.Id, rawAnswers: Map[String, Seq[String]])(implicit hc: HeaderCarrier)
      : Future[Either[ValidationErrors, ExtendedSubmission]] = {
    import cats.implicits._

    metrics.record(api) {
      http
        .post(url"${config.serviceBaseUrl}/submission/$submissionId/question/${questionId.value}")
        .withBody(Json.toJson(OutboundRecordAnswersRequest(rawAnswers)))
        .execute[HttpResponse]
        .map(resp =>
          resp.status match {
            case 200 => resp.json.as[ExtendedSubmission].asRight
            case 400 => resp.json.as[ValidationErrors].asLeft
            case _   => ValidationErrors(ValidationError(message = s"Failed to record answer for submission $submissionId and question ${questionId.value}")).asLeft
          }
        )
    }
  }

  def fetchLatestSubmissionByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[Submission]] = {
    metrics.record(api) {
      http
        .get(url"${config.serviceBaseUrl}/submission/user/${userId}")
        .execute[Option[Submission]]
    }
  }

  def createSubmission(userId: UserId, requestedBy: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[Submission]] = {
    metrics.record(api) {
      http.post(url"${config.serviceBaseUrl}/submission/user/${userId}")
        .withBody(Json.toJson(CreateSubmissionRequest(requestedBy)))
        .execute[Option[Submission]]
    }
  }

  def submitSubmission(submissionId: SubmissionId, requestedBy: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Either[String, Submission]] = {
    import cats.implicits._
    val failed = (err: UpstreamErrorResponse) => s"Failed to submit submission $submissionId"

    metrics.record(api) {
      http.post(url"${config.serviceBaseUrl}/submission/${submissionId}")
        .withBody(Json.toJson(SubmitSubmissionRequest(requestedBy)))
        .execute[Either[UpstreamErrorResponse, Submission]]
        .map(_.leftMap(failed))
    }
  }

  def fetchLatestExtendedSubmissionByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.get(url"${config.serviceBaseUrl}/submission/user/${userId}/extended")
        .execute[Option[ExtendedSubmission]]
    }
  }

  def fetchSubmission(id: SubmissionId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = {
    metrics.record(api) {
      http.get(url"${config.serviceBaseUrl}/submission/${id.value}")
        .execute[Option[ExtendedSubmission]]
    }
  }

  def fetchOrganisation(id: OrganisationId)(implicit hc: HeaderCarrier): Future[Option[Organisation]] = {
    metrics.record(api) {
      http.get(url"${config.serviceBaseUrl}/organisation/${id.value}")
        .execute[Option[Organisation]]
    }
  }

  def fetchLatestOrganisationByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[Organisation]] = {
    metrics.record(api) {
      http.get(url"${config.serviceBaseUrl}/organisation/user/$userId")
        .execute[Option[Organisation]]
    }
  }

  def createOrganisation(organisationName: OrganisationName, organisationType: Organisation.OrganisationType, userId: UserId)(implicit hc: HeaderCarrier): Future[Organisation] = {
    metrics.record(api) {
      http.post(url"${config.serviceBaseUrl}/organisation/create")
        .withBody(Json.toJson(CreateOrganisationRequest(organisationName, organisationType, userId)))
        .execute[Organisation]
    }
  }

  def addMemberToOrganisation(id: OrganisationId, email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Either[String, Organisation]] = {
    import cats.implicits._
    val failed = (err: UpstreamErrorResponse) => s"Failed to add user $email to organisation $id"

    metrics.record(api) {
      http.put(url"${config.serviceBaseUrl}/organisation/${id.value}/member")
        .withBody(Json.toJson(AddMemberRequest(email)))
        .execute[Either[UpstreamErrorResponse, Organisation]]
        .map(_.leftMap(failed))
    }
  }

  def removeMemberFromOrganisation(id: OrganisationId, userId: UserId, email: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Either[String, Organisation]] = {
    import cats.implicits._
    val failed = (err: UpstreamErrorResponse) => s"Failed to remove user $userId from organisation $id"

    metrics.record(api) {
      http.delete(url"${config.serviceBaseUrl}/organisation/${id.value}/member/$userId")
        .withBody(Json.toJson(RemoveMemberRequest(userId, email)))
        .execute[Either[UpstreamErrorResponse, Organisation]]
        .map(_.leftMap(failed))
    }
  }
}

object OrganisationConnector {
  case class Config(serviceBaseUrl: String)

  case class OutboundRecordAnswersRequest(responses: Map[String, Seq[String]])
  implicit val writesOutboundRecordAnswersRequest: Writes[OutboundRecordAnswersRequest] = Json.writes[OutboundRecordAnswersRequest]

  case class CreateSubmissionRequest(requestedBy: LaxEmailAddress)
  implicit val writesCreateSubmissionRequest: Writes[CreateSubmissionRequest] = Json.writes[CreateSubmissionRequest]

  case class SubmitSubmissionRequest(requestedBy: LaxEmailAddress)
  implicit val writesSubmitSubmissionRequest: Writes[SubmitSubmissionRequest] = Json.writes[SubmitSubmissionRequest]

  case class AddMemberRequest(email: LaxEmailAddress)
  implicit val writesAddMembersRequest: Writes[AddMemberRequest] = Json.writes[AddMemberRequest]

  case class RemoveMemberRequest(userId: UserId, email: LaxEmailAddress)
  implicit val writesRemoveMemberRequest: Writes[RemoveMemberRequest] = Json.writes[RemoveMemberRequest]

  case class CreateOrganisationRequest(organisationName: OrganisationName, organisationType: Organisation.OrganisationType, requestedBy: UserId)
  implicit val writesCreateOrganisationRequest: Writes[CreateOrganisationRequest] = Json.writes[CreateOrganisationRequest]
}

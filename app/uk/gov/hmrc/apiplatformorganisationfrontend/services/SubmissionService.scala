/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformorganisationfrontend.services

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{SubmissionId, _}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.ValidationErrors
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.OrganisationConnector

@Singleton
class SubmissionService @Inject() (organisationConnector: OrganisationConnector) {

  def createSubmission(userId: UserId, requestedBy: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[Submission]] =
    organisationConnector.createSubmission(userId, requestedBy)

  def submitSubmission(submissionId: SubmissionId, requestedBy: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Either[String, Submission]] =
    organisationConnector.submitSubmission(submissionId, requestedBy)

  def fetchLatestSubmissionByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[Submission]] = organisationConnector.fetchLatestSubmissionByUserId(userId)

  def fetchLatestExtendedSubmissionByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] =
    organisationConnector.fetchLatestExtendedSubmissionByUserId(userId)

  def fetch(id: SubmissionId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = organisationConnector.fetchSubmission(id)

  def recordAnswer(submissionId: SubmissionId, questionId: Question.Id, rawAnswers: Map[String, Seq[String]])(implicit hc: HeaderCarrier)
      : Future[Either[ValidationErrors, ExtendedSubmission]] =
    organisationConnector.recordAnswer(submissionId, questionId, rawAnswers)
}

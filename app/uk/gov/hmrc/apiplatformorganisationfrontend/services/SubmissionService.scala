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
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.*
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.ValidationErrors
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.UpdateRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ApiPlatformDeskproConnector.{Attachment, CreateTicketRequest}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ApiPlatformDeskproConnector, OrganisationConnector, ThirdPartyDeveloperConnector}

@Singleton
class SubmissionService @Inject() (
    organisationConnector: OrganisationConnector,
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    apiPlatformDeskproConnector: ApiPlatformDeskproConnector
  )(implicit val ec: ExecutionContext
  ) extends EitherTHelper[String] with Logging {

  def createSubmission(userId: UserId, requestedBy: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[Submission]] =
    organisationConnector.createSubmission(userId, requestedBy)

  def submitSubmission(submissionId: SubmissionId, userId: UserId, requestedBy: LaxEmailAddress, developer: User)(implicit hc: HeaderCarrier)
      : Future[Either[String, Submission]] = {
    (
      for {
        submission <- fromEitherF(organisationConnector.submitSubmission(submissionId, requestedBy))
        _          <- liftF(updateUserProfileIfRequired(userId, submission, developer))
        _          <- liftF(createDeskproTicketIfRequired(userId, submission, developer))
      } yield submission
    ).value
  }

  private def updateUserProfileIfRequired(userId: UserId, submission: Submission, developer: User)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    val nameAnswer = submission.getAnswerToQuestionOfInterest("responsibleIndividualNameId")
    nameAnswer match {
      case ActualAnswer.NameAnswer(FullName(Some(_), Some(firstName), Some(lastName))) if isNewName(developer, firstName, lastName) => updateUserProfile(userId, firstName, lastName)
      case _                                                                                                                        => Future.successful(None)
    }
  }

  private def isNewName(developer: User, firstName: String, lastName: String): Boolean = {
    developer.firstName != firstName || developer.lastName != lastName
  }

  private def updateUserProfile(userId: UserId, firstName: String, lastName: String)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    logger.info(s"Organisation registration updating user profile for userId: $userId")
    thirdPartyDeveloperConnector.updateProfile(userId, UpdateRequest(firstName, lastName)).map(u => Some(u))
  }

  private def createDeskproTicketIfRequired(userId: UserId, submission: Submission, developer: User)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val organisationTypeAnswer = submission.getAnswerToQuestionOfInterest("organisationTypeId")
    organisationTypeAnswer match {
      case ActualAnswer.SingleChoiceAnswer("Non-UK company without a branch or place of business in the UK") => createDeskproTicket(userId, submission, developer)
      case _                                                                                         => Future.successful(None)
    }
  }

  private def createDeskproTicket(userId: UserId, submission: Submission, developer: User)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    logger.info(s"Organisation registration creating Deskpro ticket for userId: $userId")

    val organisationName = submission.organisationName
    val attachment       = submission.attachment

    val createTicketRequest = CreateTicketRequest(
      fullName = developer.displayedName,
      email = developer.email.text,
      subject = "Organisation Registration Request",
      message = s"""${developer.displayedName} has submitted their organisation ${organisationName.getOrElse("")} for production use on the Developer Hub.""",
      organisation = organisationName,
      supportReason = Some("Organisation Registration Submission"),
      reasonKey = Some("organisation-registration-submission"),
      attachments = attachment.fold(List.empty)(a => List(Attachment(a.fileRef.getOrElse(""), a.fileName.getOrElse(""))))
    )

    apiPlatformDeskproConnector.createTicket(createTicketRequest, hc)
  }

  def fetchLatestSubmissionByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[Submission]] = organisationConnector.fetchLatestSubmissionByUserId(userId)

  def fetchLatestExtendedSubmissionByUserId(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] =
    organisationConnector.fetchLatestExtendedSubmissionByUserId(userId)

  def fetch(id: SubmissionId)(implicit hc: HeaderCarrier): Future[Option[ExtendedSubmission]] = organisationConnector.fetchSubmission(id)

  def recordAnswer(submissionId: SubmissionId, questionId: Question.Id, rawAnswers: Map[String, Seq[String]])(implicit hc: HeaderCarrier)
      : Future[Either[ValidationErrors, ExtendedSubmission]] = {
    logger.info(s"In SubmissionService.recordAnswer() rawAnswers: $rawAnswers")
    organisationConnector.recordAnswer(submissionId, questionId, rawAnswers)
  }

  def fetchAllowList(userId: UserId)(implicit hc: HeaderCarrier): Future[Option[OrganisationAllowList]] = {
    organisationConnector.fetchOrganisationAllowList(userId)
  }
}

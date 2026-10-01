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
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ApiPlatformDeskproConnector, OrganisationConnector, ThirdPartyDeveloperConnector, UpscanInitiateConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.views.UploadViewModel

@Singleton
class SubmissionService @Inject() (
    organisationConnector: OrganisationConnector,
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    apiPlatformDeskproConnector: ApiPlatformDeskproConnector,
    upscanInitiateConnector: UpscanInitiateConnector
  )(implicit val ec: ExecutionContext
  ) extends EitherTHelper[String] with Logging {

  def createSubmission(userId: UserId, requestedBy: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Option[Submission]] =
    organisationConnector.createSubmission(userId, requestedBy)

  def submitSubmission(submissionId: SubmissionId, userId: UserId, requestedBy: LaxEmailAddress, developer: User)(implicit hc: HeaderCarrier)
      : Future[Either[String, Submission]] = {
    (
      for {
        submission        <- fromEitherF(organisationConnector.submitSubmission(submissionId, requestedBy))
        _                 <- liftF(updateUserProfileIfRequired(userId, submission, developer))
        updatedSubmission <- liftF(createDeskproTicketIfRequired(submission, developer))

      } yield updatedSubmission
    ).value
  }

  private def updateUserProfileIfRequired(userId: UserId, submission: Submission, developer: User)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    val nameAnswer = submission.getAnswerToQuestionOfInterest("responsibleIndividualNameId")
    nameAnswer match {
      case ActualAnswer.ConfirmNameAnswer(ConfirmFullName(Some(_), Some(firstName), Some(lastName))) if isNewName(developer, firstName, lastName) =>
        updateUserProfile(userId, firstName, lastName)
      case _                                                                                                                                      => Future.successful(None)
    }
  }

  private def isNewName(developer: User, firstName: String, lastName: String): Boolean = {
    developer.firstName != firstName || developer.lastName != lastName
  }

  private def updateUserProfile(userId: UserId, firstName: String, lastName: String)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    logger.info(s"Organisation registration updating user profile for userId: $userId")
    thirdPartyDeveloperConnector.updateProfile(userId, UpdateRequest(firstName, lastName)).map(u => Some(u))
  }

  private def createDeskproTicketIfRequired(submission: Submission, developer: User)(implicit hc: HeaderCarrier): Future[Submission] = {
    val organisationTypeAnswer                                     = submission.getAnswerToQuestionOfInterest("organisationTypeId")
    val additionalSubmissionData: Option[AdditionalSubmissionData] = submission.additionalSubmissionData

    (organisationTypeAnswer, additionalSubmissionData) match {
      case (ActualAnswer.SingleChoiceAnswer("Non-UK company without a branch or place of business in the UK"), Some(Some(ticketId), _)) =>
        updateDeskproTicket(submission, developer, ticketId)
      case (ActualAnswer.SingleChoiceAnswer("Non-UK company without a branch or place of business in the UK"), None)                    => createDeskproTicket(submission, developer)
      case _                                                                                                                            => Future.successful(submission)
    }
  }

  private def createDeskproTicket(submission: Submission, developer: User)(implicit hc: HeaderCarrier): Future[Submission] = {
    val organisationName = submission.organisationName
    val attachment       = submission.attachment

    val createTicketRequest = CreateTicketRequest(
      fullName = developer.displayedName,
      email = developer.email.text,
      subject = "Organisation Registration Request",
      message =
        s"""${developer.displayedName} has submitted their organisation ${organisationName.getOrElse("")} for
           | use on the Developer Hub.""".stripMargin,
      organisation = organisationName,
      supportReason = Some("Organisation Registration Submission"),
      reasonKey = Some("organisation-registration-submission"),
      organisationSubmissionId = Some(submission.id.value.toString),
      attachments = attachment.fold(List.empty)(a => List(Attachment(a.fileRef.getOrElse(""), a.fileName.getOrElse(""))))
    )
    logger.info(s"Organisation registration creating Deskpro ticket for userId: ${developer.userId}, attachments: ${createTicketRequest.attachments}")
    apiPlatformDeskproConnector.createTicket(createTicketRequest, hc).map(response =>
      val updatedSubmission = submission.copy(additionalSubmissionData = Some(AdditionalSubmissionData(supportTicketId = response.id, supportTicketRef = response.ref)))
      organisationConnector.recordTicket(submission.id, response.id, response.ref)
      updatedSubmission
    )
  }

  private def updateDeskproTicket(submission: Submission, developer: User, supportTicketId: Int)(implicit hc: HeaderCarrier): Future[Submission] = {
    val organisationName = submission.organisationName
    val attachment       = submission.attachment

    apiPlatformDeskproConnector.createMessage(
      ticketId = supportTicketId,
      userEmail = developer.email,
      message = s"""${developer.displayedName} has re-submitted their organisation ${organisationName.getOrElse("")} for
                   | use on the Developer Hub.""".stripMargin,
      status = "awaiting_agent",
      attachments = attachment.fold(List.empty)(a => List(Attachment(a.fileRef.getOrElse(""), a.fileName.getOrElse("")))),
      hc
    )
    Future.successful(submission)
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

  def initiateUpscan(question: Question, submissionId: SubmissionId, returnTo: Option[String] = None)(implicit hc: HeaderCarrier): Future[Option[UploadViewModel]] = {
    question match {
      case _: Question.AttachmentQuestion =>
        upscanInitiateConnector
          .initiate(question.id, submissionId, returnTo)
          .map { upscanResponse =>
            val model = Some(
              UploadViewModel(
                upscan = upscanResponse,
                error = None
              )
            )
            model
          }
      case _                              => Future.successful(None)
    }
  }
}

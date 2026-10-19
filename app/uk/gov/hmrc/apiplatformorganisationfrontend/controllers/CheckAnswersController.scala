/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, Call, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.{ValidationError, ValidationErrors}
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.AnswersViewModel.*
import uk.gov.hmrc.apiplatformorganisationfrontend.models.views.UploadViewModel
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationActionService, SubmissionService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.*

@Singleton
class CheckAnswersController @Inject() (
    val errorHandler: ErrorHandler,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val submissionService: SubmissionService,
    val organisationActionService: OrganisationActionService,
    checkAnswersView: CheckAnswersView,
    submittedAnswersView: SubmittedAnswersView,
    submitSubmissionSuccessPage: SubmitSubmissionSuccessPage,
    sectionSummaryView: SectionSummaryView,
    questionView: QuestionView,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends LoggedInController(mcc)
    with EitherTHelper[String]
    with SubmissionActionBuilders {

  def checkAnswersPage(submissionId: SubmissionId) = withSubmission(submissionId) { implicit request =>
    submissionService.fetch(submissionId).map {
      case Some(extSubmission) =>
        val viewModel = convertSubmissionToViewModel(extSubmission)
        if (extSubmission.submission.status.isOpenToAnswers) {
          Ok(checkAnswersView(viewModel, request.msgRequest.flash.get("error")))
        } else {
          Ok(submittedAnswersView(viewModel))
        }
      case None                => BadRequest("No submission found")
    }
  }

  def checkAnswersAction(submissionId: SubmissionId) = withSubmission(submissionId) { implicit request =>
    if (hasExpiredAttachment(request.submission)) showUploadPageWithErrors()
    else {
      submissionService.submitSubmission(submissionId, request.userId, request.email, request.developer)
        .map(_ match {
          case Right(_)  => Redirect(routes.CheckAnswersController.submitSuccessPage(submissionId: SubmissionId))
          case Left(msg) => Redirect(routes.CheckAnswersController.checkAnswersPage(submissionId)).flashing("error" -> msg)
        })
    }
  }

  private def hasExpiredAttachment(submission: Submission): Boolean = {
    (submission.getQuestionOfInterest("attachmentNonUkWithoutId"), submission.additionalSubmissionData) match {
      case (_, Some(_: AdditionalSubmissionData)) => false
      case (None, _)                              => false
      case (Some(_: Question.Id), _)              =>
        val threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS)
        submission.attachment.fold(false) { attachment =>
          attachment.createdOn.fold(false)(_.isBefore(threeDaysAgo))
        }
    }
  }

  private def showUploadPageWithErrors()(implicit hc: HeaderCarrier, request: SubmissionRequest[AnyContent]): Future[Result] = {
    val submission        = request.submission
    lazy val errorMessage =
      """Your tax registration document is no longer accessible in our system as we only store uploaded documents for 3 days.
        | Click Choose file to upload your tax registration document.""".stripMargin

    submission.getQuestionOfInterest("attachmentNonUkWithoutId") match {
      case None                          => Future.successful(NotFound("Attachment not found"))
      case Some(questionId: Question.Id) =>
        val maybeQuestion      = submission.findQuestion(questionId)
        val maybeQuestionnaire = submission.findQuestionnaireContaining(questionId)

        val validationErrors = ValidationErrors(ValidationError(Question.answerKey, errorMessage))

        (maybeQuestion, maybeQuestionnaire) match {
          case (Some(question), Some(questionnaire)) =>
            submissionService.initiateUpscan(question, submission.id, None)(hc) map {
              case None                                   => InternalServerError("Error initiating Upscan")
              case Some(uploadViewModel: UploadViewModel) =>
                val call = Call(method = "POST", url = uploadViewModel.upscan.postTarget)
                Ok(questionView(
                  question = question,
                  questionnaire = questionnaire,
                  submitAction = call,
                  currentAnswers = None,
                  submission = submission,
                  errorInfo = Some(validationErrors),
                  returnTo = None,
                  uploadViewModel = Some(uploadViewModel)
                ))
            }
          case (_, _)                                => Future.successful(BadRequest("Submission not found"))
        }
    }

  }

  def submitSuccessPage(submissionId: SubmissionId) = withSubmission(submissionId) { implicit request =>
    Future.successful(Ok(submitSubmissionSuccessPage()))
  }

  def showSectionSummary(submissionId: SubmissionId, questionnaireId: Questionnaire.Id) =
    withSubmission(submissionId) { implicit request =>
      submissionService.fetch(submissionId).map {
        case Some(extSubmission) =>
          val fullViewModel     = convertSubmissionToViewModel(extSubmission)
          val filteredViewModel = fullViewModel.copy(
            questionnaires = fullViewModel.questionnaires.filter(_.id == questionnaireId)
          )

          filteredViewModel.questionnaires.headOption match {
            case Some(_) =>
              Ok(sectionSummaryView(filteredViewModel))
            case None    =>
              BadRequest("Questionnaire not found")
          }
        case None                =>
          BadRequest("No submission found")
      }
    }

  def sectionSummaryAction(submissionId: SubmissionId) =
    withSubmission(submissionId) { _ =>
      Future.successful(
        Redirect(routes.ChecklistController.checklistPage(submissionId))
      )
    }
}

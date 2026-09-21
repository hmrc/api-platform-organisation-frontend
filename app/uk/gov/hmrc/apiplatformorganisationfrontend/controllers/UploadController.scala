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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging
import play.api.i18n.Messages.implicitMessagesProviderToMessages
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents, MessagesRequest, Request, Result}
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.Question.ForwardToQuestion
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{ExtendedSubmission, Question, Questionnaire, SubmissionId}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.{ValidationError, ValidationErrors}
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ThirdPartyDeveloperConnector, UpscanInitiateConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.UserRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.models.views.UploadViewModel
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationActionService, SubmissionService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.QuestionView
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class UploadController @Inject() (
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val organisationActionService: OrganisationActionService,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val upscanInitiateConnector: UpscanInitiateConnector,
    val submissionService: SubmissionService,
    questionView: QuestionView
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends LoggedInController(mcc)
  with Logging
  with SubmissionActionBuilders {

  def upscanResultRedirect(sid: SubmissionId, qid: Question.Id): Action[AnyContent] = withSubmission(sid) { implicit request: SubmissionRequest[AnyContent] =>

    val hc            = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    val userSession = developerSessionFromRequest(request)
    val maybeFileReference = request.getQueryString("key")
    val maybeErrorCode = request.getQueryString("errorCode")
    val maybeErrorMessage = request.getQueryString("errorMessage")

    logger.info(s"In upscanResultRedirect request: " + request)
    logger.info(s"In upscanResultRedirect questionId: " + qid)
    logger.info(s"In upscanResultRedirect submissionId: " + sid)
    logger.info(s"In upscanResultRedirect fileReference: " + maybeFileReference)
    logger.info(s"In upscanResultRedirect errorCode: " + maybeErrorCode)
    logger.info(s"In upscanResultRedirect errorMessage: " + maybeErrorMessage)

    (maybeFileReference, maybeErrorCode, maybeErrorMessage) match {
      case (Some(fr), Some(ec), Some(em)) =>
        logger.warn(s"upscanResultRedirect failure submissionId:$sid, fileReference:$maybeFileReference, error:$em")
        val validationErrors = ValidationErrors(ValidationError(Question.answerKey, em))
        showQuestionViewWithErrors(sid, qid, ec, em)(hc, request)
      case (Some(fr), None, None) =>
        val answer       = Map("fileRef" -> Seq(fr))
        logger.info(s"upscanResultRedirect success submissionId:$sid, fileReference:$maybeFileReference")
        submissionService.recordAnswer(sid, qid, answer)(hc)
          .map(_.fold(failed, success(_, qid, sid)))
      case _                                =>
        Future.successful(overrideIframeHeaders(BadRequest("s file reference missing")))
    }
  }

  private def showQuestionViewWithErrors(submissionId: SubmissionId, questionId: Question.Id, errorCode:String, errorMessage: String)
                                        (implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    submissionService.fetch(submissionId)(hc).flatMap {
      case None => Future.successful(BadRequest(s"Could not find submission for submissionId: ${submissionId.toString()}"))
      case Some(extendedSubmission: ExtendedSubmission) =>
        val persistedAnswer = extendedSubmission.submission.latestInstance.answersToQuestions.get(questionId)
        val submission = extendedSubmission.submission
        val maybeQuestion = submission.findQuestion(questionId)
        val maybeQuestionnaire = submission.findQuestionnaireContaining(questionId)
        val validationErrors = ValidationErrors(ValidationError(Question.answerKey, errorMessage))
        
        (maybeQuestion, maybeQuestionnaire) match {
          case (Some(question), Some(questionnaire)) =>
            submissionService.initiateUpscan(question, submission, None)(hc) map {
              case None => BadRequest("Error calling initiateUpscan")
              case Some(uploadViewModel: UploadViewModel) =>
                val call = Call(method = "POST", url = uploadViewModel.upscan.postTarget)
                Ok(questionView(question = question, questionnaire = questionnaire, submitAction = call, currentAnswers = None,
                  submission = submission, errorInfo = Some(validationErrors), returnTo = None, uploadViewModel = Some(uploadViewModel)))
            }
          case (_, _) => Future.successful(BadRequest("submissionId, questionId or fileReference missing"))
        }
    }
  }

  private def success(extSubmission: ExtendedSubmission, questionId: Question.Id, submissionId: SubmissionId): Result = {
    val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
    val nextQuestion  = findNextQuestion(extSubmission, questionId, questionnaire.id)

    lazy val toSectionSummary =
      routes.CheckAnswersController.showSectionSummary(extSubmission.submission.id, questionnaire.id)
    lazy val toNextQuestion = (nextQuestionId: Question.Id) => routes.QuestionsController.showQuestion(submissionId, nextQuestionId)

    logger.info(s"In UploadController success() nextQuestion:$nextQuestion")

    Redirect(nextQuestion.fold(toSectionSummary)(toNextQuestion))
  }

  private def failed(errors: ValidationErrors) = InternalServerError(s"Something went wrong recording the upload file answer -> $errors")

  private def findNextQuestion(extSubmission: ExtendedSubmission, questionId: Question.Id, questionnaireId: Questionnaire.Id) = {
    extSubmission.submission.findQuestion(questionId) match {
      case Some(ForwardToQuestion(id, forwardToQuestionId, _, _, _)) => Some(forwardToQuestionId)
      case _                                                         => extSubmission.questionnaireProgress.get(questionnaireId)
          .flatMap(_.questionsToAsk.dropWhile(_ != questionId).drop(1).headOption)
    }
  }

  private def overrideIframeHeaders(result: Result) = {
    result.withHeaders(
      SecurityHeadersFilter.X_FRAME_OPTIONS_HEADER         -> "ALLOWALL",
      SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER -> "frame-ancestors *"
    )
  }
}

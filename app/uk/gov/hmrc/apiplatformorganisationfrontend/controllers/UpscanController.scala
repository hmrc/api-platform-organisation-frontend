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

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{ExtendedSubmission, Question, Questionnaire, SubmissionId}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.Question.ForwardToQuestion
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.ValidationErrors
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ApiPlatformDeskproConnector, ThirdPartyDeveloperConnector, UpscanInitiateConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.UploadStatus.{Failed, FailedUploadToDeskpro, UploadedSuccessfully}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.UploadedFile
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationActionService, SubmissionService}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanController @Inject()(
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val organisationActionService: OrganisationActionService,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val apiPlatformDeskproConnector: ApiPlatformDeskproConnector,
    val upscanInitiateConnector: UpscanInitiateConnector,
    val submissionService: SubmissionService,
    val questionsController: QuestionsController
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) {

  // Returns fresh Upscan upload fields for multi-file uploads.
  def initiateUpscan(): Action[AnyContent] = loggedInAction { implicit request =>
//    upscanInitiateConnector.initiate().map(upscanInitiateResponse =>
//      Ok(Json.toJson(upscanInitiateResponse))
//    )
    Future.successful(Ok(""))
  }

  def upscanResultRedirect(): Action[AnyContent] = Action.async { request =>

    val hc = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    val questionId = request.getQueryString("questionId")
    val submissionId = request.getQueryString("submissionId")
    val fileReference = request.getQueryString("key")

    println(s"***questionId: "+ questionId)
    println(s"***submissionId: "+ submissionId)
    println(s"***fileReference: "+ fileReference)
    println(s"***returnTo: "+ request.getQueryString("returnTo"))

    (questionId, submissionId, fileReference) match {
      case (Some(qId), Some(sId), Some(fr)) => {
        println(s"***In upscanResultRedirect match submissionId:$submissionId and questionId:$questionId")
        Thread.sleep(1000)
        apiPlatformDeskproConnector.fetchFileForFileRef(fileReference=fr, hc) map {
          case Some(fileUpload: UploadedFile) => fileUpload.uploadStatus match {
            case (uploadedSuccessfully: UploadedSuccessfully) =>
              val answer = Map ("fileRef" -> Seq(fr), "fileName" -> Seq(uploadedSuccessfully.name))
              val questionId = Question.Id(qId)
              val submissionId = SubmissionId(UUID.fromString(sId))
              submissionService.recordAnswer(submissionId, questionId, answer)(hc)
                .map(_.fold(failed, success(_, questionId, submissionId)))
            case (failed: FailedUploadToDeskpro) => Future.successful(InternalServerError(s"Failed to upload to Deskpro: ${failed.error}"))
            case (failed: Failed) => Future.successful(InternalServerError(s"Failed to upload to Deskpro: ${failed.message}  ${failed.reason}"))
          }
          case _ => Future.successful(NotFound(s"File not found for fileRef: $fr"))
        }
      }.flatten
      case _ =>
        Future.successful(overrideIframeHeaders(BadRequest("submissionId, questionId or key missing")))
    }
  }

  private def success(extSubmission: ExtendedSubmission, questionId: Question.Id, submissionId: SubmissionId) = {
    val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
    val nextQuestion = findNextQuestion(extSubmission, questionId, questionnaire.id)

    lazy val toSectionSummary =
      routes.CheckAnswersController.showSectionSummary(extSubmission.submission.id, questionnaire.id)
    lazy val toNextQuestion = (nextQuestionId) => routes.QuestionsController.showQuestion(submissionId, nextQuestionId)

    println(s"***In upscanResultRedirect match nextQuestion:$nextQuestion")

    Redirect(nextQuestion.fold(toSectionSummary)(toNextQuestion))
  }

  private def failed(errors: ValidationErrors) = InternalServerError(s"Something went wrong recording the upload file answer -> $errors")

  private def findNextQuestion(extSubmission: ExtendedSubmission, questionId: Question.Id, questionnaireId: Questionnaire.Id) = {
    extSubmission.submission.findQuestion(questionId) match {
      case Some(ForwardToQuestion(id, forwardToQuestionId, _, _, _)) => Some(forwardToQuestionId)
      case _ => extSubmission.questionnaireProgress.get(questionnaireId)
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

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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.data.NonEmptyList

import play.api.libs.crypto.CookieSigner
import play.api.libs.json.{Json, OWrites, Reads}
import play.api.mvc.{MessagesControllerComponents, _}

import uk.gov.hmrc.apiplatform.modules.common.domain.services.NonEmptyListFormatters
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{SubmissionId, _}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.ValidationErrors
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.AnswersViewModel._
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationActionService, SubmissionService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

object QuestionsController extends NonEmptyListFormatters {
  case class ErrorMessage(message: String)
  implicit val writesErrorMessage: OWrites[ErrorMessage] = Json.writes[ErrorMessage]

  case class InboundRecordAnswersRequest(answers: NonEmptyList[String])
  implicit val readsInboundRecordAnswersRequest: Reads[InboundRecordAnswersRequest] = Json.reads[InboundRecordAnswersRequest]

}

@Singleton
class QuestionsController @Inject() (
    val errorHandler: ErrorHandler,
    override val submissionService: SubmissionService,
    val organisationActionService: OrganisationActionService,
    val cookieSigner: CookieSigner,
    questionView: QuestionView,
    sectionSummaryView: SectionSummaryView,
    mcc: MessagesControllerComponents,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends LoggedInController(mcc)
    with SubmissionActionBuilders
    with EitherTHelper[String] {

  import cats.instances.future.catsStdInstancesForFuture

  private def processQuestion(
      questionId: Question.Id,
      onFormAnswer: Option[ActualAnswer],
      errorInfo: Option[ValidationErrors],
      returnTo: Option[String] = None,
      returnQnid: Option[String] = None
    )(
      submitAction: Call
    )(implicit request: SubmissionRequest[AnyContent]
    ) = {
    val persistedAnswer = request.submission.latestInstance.answersToQuestions.get(questionId)
    val submission      = request.submission
    val oQuestion       = submission.findQuestion(questionId)
    val oQuestionnaire  = submission.findQuestionnaireContaining(questionId)

    (
      for {
        flowItem      <- fromOption(oQuestion, "Question not found in questionnaire")
        question       = oQuestion.get
        questionnaire <- fromOption(oQuestionnaire, "Questionnaire not found in questionnaire")
      } yield {
        errorInfo.fold[Result](
          Ok(questionView(question, questionnaire, submitAction, persistedAnswer, None, returnTo, returnQnid))
        )(ei => BadRequest(questionView(question, questionnaire, submitAction, onFormAnswer, Some(ei), returnTo, returnQnid)))
      }
    )
      .fold[Result](BadRequest(_), identity(_))
  }

  def showQuestion(submissionId: SubmissionId, questionId: Question.Id, onFormAnswer: Option[ActualAnswer] = None, errorInfo: Option[ValidationErrors] = None): Action[AnyContent] =
    withSubmission(submissionId) { implicit request =>
      val submitAction = routes.QuestionsController.recordAnswer(submissionId, questionId)
      processQuestion(questionId, onFormAnswer, errorInfo)(submitAction)
    }

  def updateQuestion(submissionId: SubmissionId, questionId: Question.Id, onFormAnswer: Option[ActualAnswer] = None, errorInfo: Option[ValidationErrors] = None): Action[AnyContent] =
    withSubmission(submissionId) { implicit request =>
      val returnTo     = request.getQueryString("returnTo")
      val returnQnid   = request.getQueryString("qnid")
      val submitAction = routes.QuestionsController.updateAnswer(submissionId, questionId)
      processQuestion(questionId, onFormAnswer, errorInfo, returnTo, returnQnid)(submitAction)
    }

  private def processAnswer(
      submissionId: SubmissionId,
      questionId: Question.Id
    )(
      success: (ExtendedSubmission) => Future[Result]
    )(implicit request: SubmissionRequest[AnyContent]
    ) = {
    def failed(answers: List[String], trimmedAnswers: Map[String, Seq[String]]) = (errors: ValidationErrors) => {
      import cats.implicits._

      val question = request.submission.findQuestion(questionId).get

      val onFormAnswer = question match {
        case q: Question.TextQuestion    => answers.headOption.map(ActualAnswer.TextAnswer)
        case a: Question.AddressQuestion => Some(ActualAnswer.AddressAnswer(RegisteredOfficeAddress(
            trimmedAnswers.get("addressLineOne").flatMap(_.headOption),
            trimmedAnswers.get("addressLineTwo").flatMap(_.headOption),
            trimmedAnswers.get("locality").flatMap(_.headOption),
            trimmedAnswers.get("region").flatMap(_.headOption),
            trimmedAnswers.get("postcode").flatMap(_.headOption)
          )))
        case _                           => None
      }

      showQuestion(submissionId, questionId, onFormAnswer, errors.some)(request)
    }

    val formValues     = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
    val trimmedAnswers = formValues.map { case (k, v) => k -> v.map(_.trim()).filter(_.nonEmpty) }
    val rawAnswers     = formValues.get("answer").fold(List.empty[String])(_.toList.filter(_.nonEmpty))
    val answers        = rawAnswers.map(a => a.trim())

    submissionService.recordAnswer(submissionId, questionId, trimmedAnswers)
      .map(_.fold(failed(answers, trimmedAnswers), success))
      .flatten
  }

  def recordAnswer(submissionId: SubmissionId, questionId: Question.Id): Action[AnyContent] = withSubmission(submissionId) { implicit request =>
    val success = (extSubmission: ExtendedSubmission) => {
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion  = extSubmission.questionnaireProgress.get(questionnaire.id)
        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).drop(1).headOption)

      lazy val toSectionSummary =
        routes.QuestionsController.showSectionSummary(extSubmission.submission.id, questionnaire.id)
      lazy val toNextQuestion   = (nextQuestionId) => routes.QuestionsController.showQuestion(submissionId, nextQuestionId)

      successful(Redirect(nextQuestion.fold(toSectionSummary)(toNextQuestion)))
    }

    processAnswer(submissionId, questionId)(success)
  }

  def updateAnswer(submissionId: SubmissionId, questionId: Question.Id): Action[AnyContent] = withSubmission(submissionId) { implicit request =>
    def hasQuestionBeenAnswered(questionId: Question.Id) = {
      request.submission.latestInstance.answersToQuestions.get(questionId).fold(false)(_ => true)
    }

    val success = (extSubmission: ExtendedSubmission) => {
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion  = extSubmission.questionnaireProgress.get(questionnaire.id)
        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).tail.headOption)

      val returnTo             = request.body.asFormUrlEncoded.flatMap(_.get("returnTo").flatMap(_.headOption))
      val returnQnid           = request.body.asFormUrlEncoded.flatMap(_.get("returnQnid").flatMap(_.headOption))
      val isFromSectionSummary = returnTo.contains("section-summary")

      lazy val toCheckAnswers   = routes.CheckAnswersController.checkAnswersPage(request.submission.id)
      lazy val toSectionSummary = returnQnid
        .map(qnidStr => Questionnaire.Id(qnidStr))
        .map(qnid => routes.QuestionsController.showSectionSummary(request.submission.id, qnid))
        .getOrElse(routes.QuestionsController.showSectionSummary(request.submission.id, questionnaire.id))
      lazy val toNextQuestion   = (nextQuestionId: Question.Id) =>
        if (hasQuestionBeenAnswered(nextQuestionId)) {
          if (isFromSectionSummary) toSectionSummary else toCheckAnswers
        } else {
          routes.QuestionsController.updateQuestion(submissionId, nextQuestionId)
        }

      successful(Redirect(nextQuestion.fold(
        if (isFromSectionSummary) toSectionSummary else toCheckAnswers
      )(toNextQuestion)))
    }

    processAnswer(submissionId, questionId)(success)
  }

  def showSectionSummary(submissionId: SubmissionId, questionnaireId: Questionnaire.Id) =
    withSubmission(submissionId) { implicit request =>
      submissionService.fetch(submissionId).map {
        case Some(extSubmission) =>
          // Use existing conversion, then filter to one questionnaire
          val fullViewModel     = convertSubmissionToViewModel(extSubmission)
          val filteredViewModel = fullViewModel.copy(
            questionnaires = fullViewModel.questionnaires.filter(_.id == questionnaireId)
          )

          filteredViewModel.questionnaires.headOption match {
            case Some(questionnaire) =>
              Ok(sectionSummaryView(filteredViewModel, questionnaire.label))
            case None                =>
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

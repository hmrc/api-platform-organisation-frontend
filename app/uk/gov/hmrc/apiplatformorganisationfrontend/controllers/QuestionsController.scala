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
import cats.implicits.catsSyntaxOptionId

import play.api.Logging
import play.api.libs.crypto.CookieSigner
import play.api.libs.json.{Json, Reads}
import play.api.mvc.*

import uk.gov.hmrc.apiplatform.modules.common.domain.services.NonEmptyListFormatters
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.*
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.Question.ForwardToQuestion
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.services.{ValidationError, ValidationErrors}
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ThirdPartyDeveloperConnector, UpscanInitiateConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.views.UploadViewModel
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationActionService, SubmissionService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.*

object QuestionsController {
  import NonEmptyListFormatters.given

  case class InboundRecordAnswersRequest(answers: NonEmptyList[String])
  given Reads[InboundRecordAnswersRequest] = Json.reads[InboundRecordAnswersRequest]
}

@Singleton
class QuestionsController @Inject() (
    val errorHandler: ErrorHandler,
    override val submissionService: SubmissionService,
    val organisationActionService: OrganisationActionService,
    val upscanInitiateConnector: UpscanInitiateConnector,
    val cookieSigner: CookieSigner,
    questionView: QuestionView,
    mcc: MessagesControllerComponents,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends LoggedInController(mcc)
    with SubmissionActionBuilders
    with EitherTHelper[String]
    with Logging {

  import cats.instances.future.catsStdInstancesForFuture

  private def processQuestion(
      questionId: Question.Id,
      onFormAnswer: Option[ActualAnswer],
      errorInfo: Option[ValidationErrors],
      returnTo: Option[String] = None
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
        _                  <- fromOption(oQuestion, "Question not found in questionnaire")
        question            = oQuestion.get
        questionnaire      <- fromOption(oQuestionnaire, "Questionnaire not found in questionnaire")
        uploadViewModel    <- liftF(initiateUpscan(question, submission, returnTo))
        updatedSubmitAction = getSubmitAction(uploadViewModel, submitAction)
      } yield {
        errorInfo.fold[Result] {
          Ok(questionView(question, questionnaire, updatedSubmitAction, persistedAnswer, submission, None, returnTo, uploadViewModel))
        }(ei => BadRequest(questionView(question, questionnaire, submitAction, onFormAnswer, submission, Some(ei), returnTo)))
      }
    )
      .fold[Result](BadRequest(_), identity(_))
  }

  private def initiateUpscan(question: Question, submission: Submission, returnTo: Option[String])(implicit request: SubmissionRequest[AnyContent]) = {
    question match {
      case _: Question.AttachmentQuestion =>
        upscanInitiateConnector
          .initiate(question.id, submission.id, returnTo)
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

  private def getSubmitAction(uploadViewModel: Option[UploadViewModel], submitAction: Call) = {
    uploadViewModel.fold(submitAction)(model => Call(method = "POST", url = model.upscan.postTarget))
  }

  def showQuestion(submissionId: SubmissionId, questionId: Question.Id, onFormAnswer: Option[ActualAnswer] = None, errorInfo: Option[ValidationErrors] = None): Action[AnyContent] =
    withSubmission(submissionId) { implicit request =>
      val submitAction = routes.QuestionsController.recordAnswer(submissionId, questionId)
      processQuestion(questionId, onFormAnswer, errorInfo)(submitAction)
    }

  def updateQuestion(
      submissionId: SubmissionId,
      questionId: Question.Id,
      onFormAnswer: Option[ActualAnswer] = None,
      errorInfo: Option[ValidationErrors] = None,
      returnToSection: Option[String] = None
    ): Action[AnyContent] =
    withSubmission(submissionId) { implicit request =>
      val returnTo     = returnToSection.fold(request.getQueryString("returnTo"))(r => Some(r))
      val submitAction = routes.QuestionsController.updateAnswer(submissionId, questionId)
      processQuestion(questionId, onFormAnswer, errorInfo, returnTo)(submitAction)
    }

  private def processAnswer(
      submissionId: SubmissionId,
      questionId: Question.Id
    )(
      success: (ExtendedSubmission) => Future[Result],
      failed: (List[String], Map[String, Seq[String]], ValidationErrors) => Future[Result]
    )(implicit request: SubmissionRequest[AnyContent]
    ) = {

    val formValues     = request.body.asFormUrlEncoded.get.filterNot(_._1 == "csrfToken")
    val trimmedAnswers = formValues.map { case (k, v) => k -> v.map(_.trim()).filter(_.nonEmpty) }
    val rawAnswers     = formValues.get("answer").fold(List.empty[String])(_.toList.filter(_.nonEmpty))
    val answers        = rawAnswers.map(a => a.trim())
    val question       = request.submission.findQuestion(questionId).get

    val onFormAnswer = question match {
      case _: Question.NameQuestion => Some(ActualAnswer.NameAnswer(FullName(
          trimmedAnswers.get("isThisYourName").flatMap(_.headOption),
          trimmedAnswers.get("firstName").flatMap(_.headOption),
          trimmedAnswers.get("lastName").flatMap(_.headOption)
        )))
      case _                        => None
    }

    val trimmedNameAnswers = onFormAnswer match {
      case Some(ActualAnswer.NameAnswer(FullName(Some("Yes"), Some(_), Some(_)))) => trimmedAnswers ++ Map(
          "firstName" -> Seq(request.developer.firstName),
          "lastName"  -> Seq(request.developer.lastName)
        )
      case _                                                                      => trimmedAnswers
    }

    submissionService.recordAnswer(submissionId, questionId, trimmedNameAnswers)
      .map(_.fold(errs => failed(answers, trimmedNameAnswers, errs), success))
      .flatten
  }

  private def redisplayQuestion(
      questionId: Question.Id,
      submission: Submission,
      answers: List[String],
      trimmedAnswers: Map[String, Seq[String]],
      errors: ValidationErrors,
      isUpdate: Boolean,
      returnTo: Option[String]
    )(implicit request: SubmissionRequest[AnyContent]
    ) = {
    import cats.implicits.*

    val question = submission.findQuestion(questionId).get

    val onFormAnswer = question match {
      case _: Question.TextQuestion          => answers.headOption.map(ActualAnswer.TextAnswer.apply)
      case _: Question.AddressQuestion       => Some(ActualAnswer.AddressAnswer(RegisteredOfficeAddress(
          trimmedAnswers.get("addressLineOne").flatMap(_.headOption),
          trimmedAnswers.get("addressLineTwo").flatMap(_.headOption),
          trimmedAnswers.get("locality").flatMap(_.headOption),
          trimmedAnswers.get("region").flatMap(_.headOption),
          trimmedAnswers.get("postcode").flatMap(_.headOption)
        )))
      case _: Question.NameQuestion          => Some(ActualAnswer.NameAnswer(FullName(
          trimmedAnswers.get("isThisYourName").flatMap(_.headOption),
          trimmedAnswers.get("firstName").flatMap(_.headOption),
          trimmedAnswers.get("lastName").flatMap(_.headOption)
        )))
      case _: Question.CompanyNumberQuestion => answers.headOption.map(ActualAnswer.CompanyNumberAnswer.apply)
      case _                                 => None
    }

    if (isUpdate) {
      updateQuestion(submission.id, questionId, onFormAnswer, errors.some, returnTo)(request)
    } else {
      showQuestion(submission.id, questionId, onFormAnswer, errors.some)(request)
    }
  }

  private def findNextQuestion(extSubmission: ExtendedSubmission, questionId: Question.Id, questionnaireId: Questionnaire.Id) = {
    extSubmission.submission.findQuestion(questionId) match {
      case Some(ForwardToQuestion(id, forwardToQuestionId, _, _, _)) => Some(forwardToQuestionId)
      case _                                                         => extSubmission.questionnaireProgress.get(questionnaireId)
          .flatMap(_.questionsToAsk.dropWhile(_ != questionId).drop(1).headOption)
    }
  }

  def recordAnswer(submissionId: SubmissionId, questionId: Question.Id): Action[AnyContent] = withSubmission(submissionId) { implicit request =>
    val success = (extSubmission: ExtendedSubmission) => {
      val questionnaire = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val nextQuestion  = findNextQuestion(extSubmission, questionId, questionnaire.id)

      lazy val toSectionSummary =
        routes.CheckAnswersController.showSectionSummary(extSubmission.submission.id, questionnaire.id)
      lazy val toNextQuestion   = (nextQuestionId) => routes.QuestionsController.showQuestion(submissionId, nextQuestionId)

      successful(Redirect(nextQuestion.fold(toSectionSummary)(toNextQuestion)))
    }

    val failed = (answers: List[String], trimmedAnswers: Map[String, Seq[String]], errors: ValidationErrors) => {
      if (errors.errors.exists(_.key == ValidationError.companyNumberNotFoundKey)) {
        successful(Redirect(routes.OrganisationRegistrationController.companyNumberNotFoundView(submissionId, questionId)))
      } else {
        redisplayQuestion(questionId, request.submission, answers, trimmedAnswers, errors, false, None)
      }
    }

    processAnswer(submissionId, questionId)(success, failed)
  }

  def updateAnswer(submissionId: SubmissionId, questionId: Question.Id): Action[AnyContent] = withSubmission(submissionId) { implicit request =>
    val returnTo = request.body.asFormUrlEncoded.flatMap(_.get("returnTo").flatMap(_.headOption)).getOrElse("check-answers")

    def hasQuestionBeenAnswered(questionId: Question.Id) = {
      request.submission.latestInstance.answersToQuestions.get(questionId).fold(false)(_ => true)
    }

    val success = (extSubmission: ExtendedSubmission) => {
      val questionnaire       = extSubmission.submission.findQuestionnaireContaining(questionId).get
      val maybeNextQuestionId = extSubmission.questionnaireProgress.get(questionnaire.id)
        .flatMap(_.questionsToAsk.dropWhile(_ != questionId).tail.headOption)

      val isFromSectionSummary = returnTo.contains("section-summary")

      lazy val toCheckAnswers   = routes.CheckAnswersController.checkAnswersPage(request.submission.id)
      lazy val toSectionSummary = routes.CheckAnswersController.showSectionSummary(request.submission.id, questionnaire.id)
      lazy val toNextQuestion   = maybeNextQuestionId match {
        case Some(nextQuestionId) => {
          if (hasQuestionBeenAnswered(nextQuestionId)) {
            if (isFromSectionSummary) toSectionSummary else toCheckAnswers
          } else {
            routes.QuestionsController.updateQuestion(submissionId, nextQuestionId)
          }
        }
        case _                    =>
          if (isFromSectionSummary) toSectionSummary else toCheckAnswers
      }

      successful(Redirect(toNextQuestion))
    }

    val failed = (answers: List[String], trimmedAnswers: Map[String, Seq[String]], errors: ValidationErrors) => {
      if (errors.errors.exists(_.key == ValidationError.companyNumberNotFoundKey)) {
        successful(Redirect(routes.OrganisationRegistrationController.companyNumberNotFoundUpdateView(submissionId, questionId, returnTo)))
      } else {
        redisplayQuestion(questionId, request.submission, answers, trimmedAnswers, errors, true, Some(returnTo))(request)
      }
    }

    processAnswer(submissionId, questionId)(success, failed)
  }
}

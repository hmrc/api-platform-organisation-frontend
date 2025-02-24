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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.AnswersViewModel._
import uk.gov.hmrc.apiplatformorganisationfrontend.services.SubmissionService
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

@Singleton
class CheckAnswersController @Inject() (
    val errorHandler: ErrorHandler,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val submissionService: SubmissionService,
    checkAnswersView: CheckAnswersView,
    submittedAnswersView: SubmittedAnswersView,
    submitSubmissionSuccessPage: SubmitSubmissionSuccessPage,
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
    submissionService.submitSubmission(submissionId, request.email)
      .map(_ match {
        case Right(sub) => Redirect(routes.CheckAnswersController.submitSuccessPage(submissionId: SubmissionId))
        case Left(msg)  => Redirect(routes.CheckAnswersController.checkAnswersPage(submissionId)).flashing("error" -> msg)
      })
  }

  def submitSuccessPage(submissionId: SubmissionId) = withSubmission(submissionId) { implicit request =>
    Future.successful(Ok(submitSubmissionSuccessPage()))
  }
}

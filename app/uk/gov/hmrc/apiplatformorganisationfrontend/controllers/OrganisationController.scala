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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.CreateOrganisationForm.toRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.models._
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationService, SubmissionService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

final case class CreateOrganisationForm(organisationName: String)

object CreateOrganisationForm {

  val form: Form[CreateOrganisationForm] = Form(mapping(
    "organisation-name" -> nonEmptyText
  )(CreateOrganisationForm.apply)(CreateOrganisationForm.unapply))

  def toRequest(form: CreateOrganisationForm): CreateOrganisationRequest = {
    CreateOrganisationRequest(OrganisationName(form.organisationName))
  }
}

@Singleton
class OrganisationController @Inject() (
    mcc: MessagesControllerComponents,
    createPage: CreateOrganisationPage,
    successPage: CreateOrganisationSuccessPage,
    landingPage: OrganisationLandingPage,
    mainlandingPage: MainLandingPage,
    organisationService: OrganisationService,
    submissionService: SubmissionService,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) {

  val createOrganisationView: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(createPage(CreateOrganisationForm.form)))
  }

  val createOrganisationAction: Action[AnyContent] = Action.async { implicit request =>
    def handleInvalidForm(form: Form[CreateOrganisationForm]) = {
      Future.successful(BadRequest(createPage(form)))
    }

    def handleValidForm(form: CreateOrganisationForm) = {
      organisationService.createOrganisation(toRequest(form)).map(org => Ok(successPage(org.organisationName)))
    }

    CreateOrganisationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  val organisationLandingView: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(landingPage(Some(request.userSession))))
  }

  val mainLandingView: Action[AnyContent] = loggedInAction { implicit request =>
    submissionService.fetchLatestSubmissionByUserId(request.userId).flatMap {
      case Some(submission) => Future.successful(Ok(mainlandingPage(Some(request.userSession), Some(submission.id), submission.status.isOpenToAnswers)))
      case _                => Future.successful(Ok(mainlandingPage(Some(request.userSession), None)))
    }
  }

  val organisationLandingAction: Action[AnyContent] = loggedInAction { implicit request =>
    submissionService.createSubmission(request.userId, request.email).map {
      case Some(submission) => Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.ChecklistController.checklistPage(submission.id))
      case _                => BadRequest("No submission created")
    }
  }
}

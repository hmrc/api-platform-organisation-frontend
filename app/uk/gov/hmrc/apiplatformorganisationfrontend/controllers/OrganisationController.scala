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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.CreateOrganisationForm.toRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.models._
import uk.gov.hmrc.apiplatformorganisationfrontend.services.OrganisationService
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

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
    service: OrganisationService
  )(implicit ec: ExecutionContext
  ) extends FrontendController(mcc) {

  val createOrganisationView: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(createPage(CreateOrganisationForm.form)))
  }

  val createOrganisationAction: Action[AnyContent] = Action.async { implicit request =>
    def handleInvalidForm(form: Form[CreateOrganisationForm]) = {
      Future.successful(BadRequest(createPage(form)))
    }

    def handleValidForm(form: CreateOrganisationForm) = {
      service.createOrganisation(toRequest(form)).map(org => Ok(successPage(org.organisationName)))
    }

    CreateOrganisationForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  val organisationLandingView: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok(landingPage()))
  }
}

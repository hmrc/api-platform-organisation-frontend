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
import play.api.data.Forms.mapping
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationId
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationName
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{OrganisationConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.FormUtils.oneOf
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationActionService, OrganisationService, SubmissionService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

object OrganisationController {
  case class OrganisationHomePageViewModel(organisationId: OrganisationId, organisationName: OrganisationName)

  case class CheckResponsibleIndividualForm(confirm: String)

  object CheckResponsibleIndividualForm {

    def form: Form[CheckResponsibleIndividualForm] = Form(
      mapping(
        "confirmResponsibleIndividual" -> oneOf("yes", "no")
      )(CheckResponsibleIndividualForm.apply)(CheckResponsibleIndividualForm.unapply)
    )
  }
}

@Singleton
class OrganisationController @Inject() (
    mcc: MessagesControllerComponents,
    beforeYouStartPage: BeforeYouStartPage,
    checkResponsibleIndividualPage: CheckResponsibleIndividualPage,
    landingPage: LandingPage,
    organisationHomePage: OrganisationHomePage,
    submissionService: SubmissionService,
    organisationService: OrganisationService,
    organisationConnector: OrganisationConnector,
    val organisationActionService: OrganisationActionService,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) {

  import OrganisationController._

  val checkResponsibleIndividualForm: Form[CheckResponsibleIndividualForm] = CheckResponsibleIndividualForm.form

  val landingView: Action[AnyContent] = loggedInAction { implicit request =>
    submissionService.fetchLatestSubmissionByUserId(request.userId).flatMap {
      case Some(submission) => Future.successful(Ok(landingPage(Some(request.userSession), Some(submission.id), submission.status.isOpenToAnswers)))
      case _                => Future.successful(Ok(landingPage(Some(request.userSession), None)))
    }
  }

  val beforeYouStartView: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(beforeYouStartPage(Some(request.userSession))))
  }

  val beforeYouStartAction: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.OrganisationController.checkResponsibleIndividualView))
  }

  val checkResponsibleIndividualView: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(checkResponsibleIndividualPage(Some(request.userSession), checkResponsibleIndividualForm)))
  }

  val checkResponsibleIndividualAction: Action[AnyContent] = loggedInAction { implicit request =>
    checkResponsibleIndividualForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(checkResponsibleIndividualPage(Some(request.userSession), formWithErrors)))
      },
      data => {
        submissionService.createSubmission(request.userId, request.email).map {
          case Some(submission) => Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.ChecklistController.checklistPage(submission.id))
          case _                => BadRequest("No submission created")
        }
      }
    )
  }

  def organisationHomePage(organisationId: OrganisationId): Action[AnyContent] = loggedInAction { implicit request =>
    organisationService.fetch(organisationId).map {
      case Some(org) => Ok(organisationHomePage(Some(request.userSession), OrganisationHomePageViewModel(org.id, org.organisationName)))
      case _         => BadRequest("No organisation found")
    }
  }
}

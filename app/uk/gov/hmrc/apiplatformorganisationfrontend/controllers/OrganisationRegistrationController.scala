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

import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{OrganisationConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.FormUtils.oneOf
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{OrganisationActionService, OrganisationService, SubmissionService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

object OrganisationRegistrationController {
  case class CheckResponsibleIndividualForm(confirmResponsibleIndividual: String)

  object CheckResponsibleIndividualForm {

    def form: Form[CheckResponsibleIndividualForm] = Form(
      mapping(
        "confirmResponsibleIndividual" -> oneOf("yes", "no")
      )(CheckResponsibleIndividualForm.apply)(CheckResponsibleIndividualForm.unapply)
    )
  }
}

@Singleton
class OrganisationRegistrationController @Inject() (
    mcc: MessagesControllerComponents,
    beforeYouStartPage: BeforeYouStartPage,
    checkResponsibleIndividualPage: CheckResponsibleIndividualPage,
    notResponsibleIndividualPage: NotResponsibleIndividualPage,
    notAllowListedPage: NotAllowListedPage,
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

  import OrganisationRegistrationController._

  val checkResponsibleIndividualForm: Form[CheckResponsibleIndividualForm] = CheckResponsibleIndividualForm.form

  def beforeYouStartView(): Action[AnyContent] = loggedInAction { implicit request =>
    submissionService.fetchLatestSubmissionByUserId(request.userId).flatMap {
      case Some(submission) => Future.successful(Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.ChecklistController.checklistPage(submission.id)))
      case _                => Future.successful(Ok(beforeYouStartPage(Some(request.userSession))))
    }
  }

  def beforeYouStartAction(): Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.OrganisationRegistrationController.checkResponsibleIndividualView))
  }

  val notAllowListedView: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(notAllowListedPage(Some(request.userSession))))
  }

  val checkResponsibleIndividualView: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(checkResponsibleIndividualPage(Some(request.userSession), checkResponsibleIndividualForm)))
  }

  def checkResponsibleIndividualAction(): Action[AnyContent] = loggedInAction { implicit request =>
    checkResponsibleIndividualForm.bindFromRequest().fold(
      formWithErrors => {
        Future.successful(BadRequest(checkResponsibleIndividualPage(Some(request.userSession), formWithErrors)))
      },
      formData => {
        if (formData.confirmResponsibleIndividual == "yes") {
          submissionService.createSubmission(request.userId, request.email).map {
            case Some(submission) => Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.ChecklistController.checklistPage(submission.id))
            case _                => BadRequest("No submission created")
          }
        } else {
          Future.successful(Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.OrganisationRegistrationController.notResponsibleIndividualView))
        }
      }
    )
  }

  val notResponsibleIndividualView: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(notResponsibleIndividualPage(Some(request.userSession))))
  }
}

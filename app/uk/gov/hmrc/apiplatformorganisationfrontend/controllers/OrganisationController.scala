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

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationId
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Organisation, OrganisationName}
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{OrganisationConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.services.SubmissionService
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

@Singleton
class OrganisationController @Inject() (
    mcc: MessagesControllerComponents,
    beforeYouStartPage: BeforeYouStartPage,
    landingPage: LandingPage,
    submissionService: SubmissionService,
    organisationConnector: OrganisationConnector,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) {

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
    submissionService.createSubmission(request.userId, request.email).map {
      case Some(submission) => Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.ChecklistController.checklistPage(submission.id))
      case _                => BadRequest("No submission created")
    }
  }

  def forwardToManageMembers: Action[AnyContent] = loggedInAction { implicit request =>
    def createOrg(): Future[OrganisationId] = {
      organisationConnector.createOrganisation(
        OrganisationName(request.userSession.developer.firstName + "'s Organisation"),
        Organisation.OrganisationType.UkLimitedCompany,
        request.userSession.developer.userId
      ).map {
        org => org.id
      }
    }

    def getOrgId(): Future[OrganisationId] = {
      organisationConnector.fetchLatestOrganisationByUserId(request.userSession.developer.userId).flatMap {
        maybeOrg: Option[Organisation] => maybeOrg.fold(createOrg())(org => Future.successful(org.id))
      }
    }

    getOrgId().map(orgId => Redirect(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.ManageMembersController.manageMembers(orgId)))
  }
}

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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{OrganisationId, OrganisationName}
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.services.OrganisationService
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

object ManageMembersController {
  case class ManageMembersViewModel(organisationId: OrganisationId, organisationName: OrganisationName, members: Set[LaxEmailAddress])
}

@Singleton
class ManageMembersController @Inject() (
    mcc: MessagesControllerComponents,
    manageMembersPage: ManageMembersPage,
    organisationService: OrganisationService,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) {

  import ManageMembersController._

  def manageMembers(organisationId: OrganisationId): Action[AnyContent] = loggedInAction { implicit request =>
    organisationService.fetch(organisationId).map {
      case Some(org) => {
        val viewModel = ManageMembersViewModel(org.id, org.organisationName, org.members.map(m => m.emailAddress))
        Ok(manageMembersPage(Some(request.userSession), viewModel))
      }
      case _         => BadRequest("Organisation not found")
    }
  }

  def removeMemberAction(organisationId: OrganisationId, emailAddress: LaxEmailAddress): Action[AnyContent] = loggedInAction { implicit request =>
    val viewModel = ManageMembersViewModel(OrganisationId.random, OrganisationName("Org name"), Set(LaxEmailAddress("bob@example.com")))
    Future.successful(Ok(manageMembersPage(Some(request.userSession), viewModel)))
  }

}

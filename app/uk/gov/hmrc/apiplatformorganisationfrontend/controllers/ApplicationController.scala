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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging
import play.api.data.Form
import play.api.data.Forms.{mapping, seq, text}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId, Environment, OrganisationId}
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.Organisation
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.ApplicationController.AppViewModel.fromApplicationWithCollaborators
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.ApplicationController.{ManageApplicationsViewModel, SelectedAppsForm}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.UserRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{ApplicationService, OrganisationActionService, OrganisationService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.application.{AddApplicationsSuccessView, AddApplicationsView}

object ApplicationController {

  case class AppViewModel(appId: ApplicationId, environment: Environment, appName: String)

  object AppViewModel {

    def fromApplicationWithCollaborators(applicationWithCollaborators: ApplicationWithCollaborators) = {
      AppViewModel(appId = applicationWithCollaborators.id, environment = applicationWithCollaborators.deployedTo, appName = applicationWithCollaborators.name.value)
    }
  }

  case class ManageApplicationsViewModel(organisationId: OrganisationId, organisationName: String, principalApps: List[AppViewModel], subordinateApps: List[AppViewModel])

  final case class SelectedAppsForm(selectedPrincipalApps: Seq[String], selectedSubordinateApps: Seq[String])

  object SelectedAppsForm {

    def form: Form[SelectedAppsForm] = Form(mapping(
      "selectedPrincipalApps"   -> seq(text),
      "selectedSubordinateApps" -> seq(text)
    )(SelectedAppsForm.apply)(SelectedAppsForm.unapply)
      .verifying(
        "You must select at least one application",
        data =>
          if (data.selectedPrincipalApps.isEmpty && data.selectedSubordinateApps.isEmpty) false else true
      ))
  }
}

@Singleton
class ApplicationController @Inject() (
    mcc: MessagesControllerComponents,
    val applicationService: ApplicationService,
    val organisationService: OrganisationService,
    val organisationActionService: OrganisationActionService,
    val addApplicationsView: AddApplicationsView,
    val addApplicationsSuccessView: AddApplicationsSuccessView,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val errorHandler: ErrorHandler,
    val cookieSigner: CookieSigner
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) with Logging {

  val selectedAppsForm: Form[SelectedAppsForm] = SelectedAppsForm.form

  def addApplications(organisationId: OrganisationId): Action[AnyContent] = whenTeamMemberOnOrg(organisationId) { implicit request =>
    maybeManageApplicationsViewModel(organisationId) map {
      case Some(viewModel) => Ok(addApplicationsView(Some(request.userSession), selectedAppsForm, viewModel))
      case None            => BadRequest("Organisation not found")
    }

  }

  def addApplicationsAction(organisationId: OrganisationId): Action[AnyContent] = whenTeamMemberOnOrg(organisationId) { implicit request =>
    def handleInvalidForm(formWithErrors: Form[SelectedAppsForm]) = {
      maybeManageApplicationsViewModel(organisationId) map {
        case Some(viewModel) => BadRequest(addApplicationsView(Some(request.userSession), formWithErrors, viewModel))
        case None            => BadRequest("Organisation not found")
      }
    }

    def handleValidForm(validForm: SelectedAppsForm) = {
      maybeManageApplicationsViewModel(organisationId) flatMap {
        case Some(viewModel) =>
          for {
            _ <- applicationService.addOrgToApps(
                   Actors.AppCollaborator(request.userSession.developer.email),
                   organisationId,
                   validForm.selectedPrincipalApps ++ validForm.selectedSubordinateApps
                 )
          } yield Ok(addApplicationsSuccessView(filterViewModelForSelectedApps(viewModel, validForm.selectedPrincipalApps, validForm.selectedSubordinateApps)))
        case None            => successful(BadRequest("Organisation not found"))
      }
    }

    selectedAppsForm.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  private def maybeManageApplicationsViewModel(organisationId: OrganisationId)(implicit hc: HeaderCarrier, request: UserRequest[AnyContent])
      : Future[Option[ManageApplicationsViewModel]] = {
    organisationService.fetch(organisationId).flatMap {
      case Some(org: Organisation) =>
        for {
          apps     <- applicationService.getAppsForResponsibleIndividualOrAdmin(request.userSession.developer.email)
          viewModel = assembleModel(organisationId, org.organisationName.value, apps)
        } yield Some(viewModel)
      case _                       => successful(None)
    }
  }

  private def filterViewModelForSelectedApps(
      manageApplicationsViewModel: ManageApplicationsViewModel,
      selectedPrincipalAppIds: Seq[String],
      selectedSubordinateAppIds: Seq[String]
    ): ManageApplicationsViewModel = {
    manageApplicationsViewModel.copy(
      principalApps = manageApplicationsViewModel.principalApps.filter(app => selectedPrincipalAppIds.contains(app.appId.toString())),
      subordinateApps = manageApplicationsViewModel.subordinateApps.filter(app => selectedSubordinateAppIds.contains(app.appId.toString()))
    )
  }

  private def assembleModel(organisationId: OrganisationId, organisationName: String, apps: List[ApplicationWithCollaborators]) = {

    val partitioned: (List[ApplicationWithCollaborators], List[ApplicationWithCollaborators]) = apps.partitionMap {
      case principal: ApplicationWithCollaborators if principal.deployedTo == Environment.PRODUCTION  => Left(principal)
      case subordinate: ApplicationWithCollaborators if subordinate.deployedTo == Environment.SANDBOX => Right(subordinate)
    }

    val principalApps   = partitioned._1.map(app => fromApplicationWithCollaborators(app))
    val subordinateApps = partitioned._2.map(app => fromApplicationWithCollaborators(app))

    ManageApplicationsViewModel(organisationId, organisationName, principalApps = principalApps, subordinateApps = subordinateApps)
  }
}

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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId, Environment, OrganisationId}
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.Organisation
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.ApplicationController.AppViewModel.fromApplicationWithCollaborators
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.ApplicationController.{ManageApplicationsViewModel, SelectedAppsForm}
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{ApplicationService, OrganisationService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.application.AddApplicationsView

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
    val addApplicationsView: AddApplicationsView,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val errorHandler: ErrorHandler,
    val cookieSigner: CookieSigner
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) with Logging {

  val selectedAppsForm: Form[SelectedAppsForm] = SelectedAppsForm.form

  def addApplications(organisationId: OrganisationId): Action[AnyContent] = loggedInAction { implicit request =>
    organisationService.fetch(organisationId).map {
      case Some(org: Organisation) =>
        for {
          apps     <- applicationService.getAppsForResponsibleIndividualOrAdmin(request.userSession.developer.email)
          viewModel = createManageApplicationsViewModel(organisationId, org.organisationName.value, apps)
        } yield Ok(addApplicationsView(Some(request.userSession), selectedAppsForm, viewModel))
      case _                       => successful(BadRequest("Organisation not found"))
    }.flatten

  }

  def addApplicationsAction(organisationId: OrganisationId): Action[AnyContent] = loggedInAction {

    implicit request =>
      def x(organisationId: OrganisationId): Future[Option[ManageApplicationsViewModel]] = {
        organisationService.fetch(organisationId).flatMap {
          case Some(org: Organisation) =>
            for {
              apps                                  <- applicationService.getAppsForResponsibleIndividualOrAdmin(request.userSession.developer.email)
              viewModel: ManageApplicationsViewModel = createManageApplicationsViewModel(organisationId, org.organisationName.value, apps)
            } yield Some(viewModel)
          case _                       => successful(None)
        }
      }

      selectedAppsForm.bindFromRequest().fold(
        formWithErrors => {
          x(organisationId) map {
            case Some(viewModel) => BadRequest(addApplicationsView(Some(request.userSession), formWithErrors, viewModel))
            case None            => BadRequest("Organisation not found")
          }
        },
        selectedApps =>
          x(organisationId) flatMap {
            case Some(viewModel) =>
              for {
                _ <- applicationService.addOrgToApps(
                       Actors.AppCollaborator(request.userSession.developer.email),
                       organisationId,
                       selectedApps.selectedPrincipalApps ++ selectedApps.selectedSubordinateApps
                     )

              } yield Ok(
                s"organisationId: ${organisationId.value} was added to the following principal apps: ${selectedApps.selectedPrincipalApps} and subordinate apps: ${selectedApps.selectedSubordinateApps}"
              )

          }
      )
  }

  private def createManageApplicationsViewModel(organisationId: OrganisationId, organisationName: String, apps: List[ApplicationWithCollaborators]) = {

    val partitioned: (List[ApplicationWithCollaborators], List[ApplicationWithCollaborators]) = apps.partitionMap {
      case principal: ApplicationWithCollaborators if principal.deployedTo == Environment.PRODUCTION  => Left(principal)
      case subordinate: ApplicationWithCollaborators if subordinate.deployedTo == Environment.SANDBOX => Right(subordinate)
    }

    val principalApps   = partitioned._1.map(app => fromApplicationWithCollaborators(app))
    val subordinateApps = partitioned._2.map(app => fromApplicationWithCollaborators(app))

    ManageApplicationsViewModel(organisationId, organisationName, principalApps = principalApps, subordinateApps = subordinateApps)
  }

  def recovery: PartialFunction[Throwable, Result] = {
    case e: Throwable =>
      logger.error(s"Error occurred: ${e.getMessage}", e)
      handleException(e)
  }

  private[controllers] def handleException(e: Throwable) = {
    logger.error(s"An unexpected error occurred: ${e.getMessage}", e)
    InternalServerError(Json.obj(
      "code"    -> "UNKNOWN_ERROR",
      "message" -> "Unknown error occurred"
    ))
  }
}

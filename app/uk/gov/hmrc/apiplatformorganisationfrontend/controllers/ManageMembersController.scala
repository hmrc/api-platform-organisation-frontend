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
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{OrganisationId, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.RegisteredOrUnregisteredUser
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.FormUtils.emailValidator
import uk.gov.hmrc.apiplatformorganisationfrontend.services.OrganisationService
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

object ManageMembersController {
  case class ManageMembersViewModel(organisationId: OrganisationId, organisationName: OrganisationName, members: List[RegisteredOrUnregisteredUser])
  case class AddMemberViewModel(organisationId: OrganisationId, organisationName: OrganisationName)
  case class RemoveMemberViewModel(organisationId: OrganisationId, organisationName: OrganisationName, member: RegisteredOrUnregisteredUser)

  case class AddMemberForm(email: String)

  object AddMemberForm {

    def form: Form[AddMemberForm] = Form(
      mapping(
        "email" -> emailValidator()
      )(AddMemberForm.apply)(AddMemberForm.unapply)
    )
  }

  case class RemoveMemberForm(confirm: Option[String] = Some(""))

  object RemoveMemberForm {

    def form: Form[RemoveMemberForm] = Form(
      mapping(
        "confirm" -> optional(text)
          .verifying("member.error.confirmation.no.choice.field", _.isDefined)
      )(RemoveMemberForm.apply)(RemoveMemberForm.unapply)
    )
  }
}

@Singleton
class ManageMembersController @Inject() (
    mcc: MessagesControllerComponents,
    manageMembersPage: ManageMembersPage,
    addMemberPage: AddMemberPage,
    removeMemberPage: RemoveMemberPage,
    organisationService: OrganisationService,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) {

  import ManageMembersController._

  val addMemberForm: Form[AddMemberForm]       = AddMemberForm.form
  val removeMemberForm: Form[RemoveMemberForm] = RemoveMemberForm.form

  def manageMembers(organisationId: OrganisationId): Action[AnyContent] = loggedInAction { implicit request =>
    organisationService.fetchWithAllMembersDetails(organisationId)
      .map(_ match {
        case Right(org) => {
          val viewModel = ManageMembersViewModel(org.organisation.id, org.organisation.organisationName, org.members)
          Ok(manageMembersPage(Some(request.userSession), viewModel))
        }
        case Left(msg)  => BadRequest(msg)
      })
  }

  def addMember(organisationId: OrganisationId): Action[AnyContent] = loggedInAction { implicit request =>
    organisationService.fetch(organisationId) map {
      case Some(org) => Ok(addMemberPage(Some(request.userSession), addMemberForm, AddMemberViewModel(org.id, org.organisationName)))
      case _         => BadRequest("Organisation not found")
    }
  }

  def addMemberAction(organisationId: OrganisationId): Action[AnyContent] = loggedInAction { implicit request =>
    addMemberForm.bindFromRequest().fold(
      formWithErrors => {
        organisationService.fetch(organisationId) map {
          case Some(org) => BadRequest(addMemberPage(Some(request.userSession), formWithErrors, AddMemberViewModel(organisationId, org.organisationName)))
          case _         => BadRequest("Organisation not found")
        }
      },
      memberAddData => {
        organisationService.addMemberToOrganisation(organisationId, LaxEmailAddress(memberAddData.email))
          .map(_ match {
            case Right(org) => Redirect(routes.ManageMembersController.manageMembers(organisationId))
            case Left(msg)  => BadRequest(msg)
          })
      }
    )
  }

  def removeMember(organisationId: OrganisationId, userId: UserId): Action[AnyContent] = loggedInAction { implicit request =>
    organisationService.fetchWithMemberDetails(organisationId, userId)
      .map(_ match {
        case Right(org) => {
          val viewModel = RemoveMemberViewModel(org.organisation.id, org.organisation.organisationName, org.member)
          Ok(removeMemberPage(Some(request.userSession), removeMemberForm, viewModel))
        }
        case Left(msg)  => BadRequest(msg)
      })
  }

  def removeMemberAction(organisationId: OrganisationId, userId: UserId): Action[AnyContent] = loggedInAction { implicit request =>
    removeMemberForm.bindFromRequest().fold(
      formWithErrors => {
        organisationService.fetchWithMemberDetails(organisationId, userId)
          .map(_ match {
            case Right(org) => {
              val viewModel = RemoveMemberViewModel(org.organisation.id, org.organisation.organisationName, org.member)
              BadRequest(removeMemberPage(Some(request.userSession), formWithErrors, viewModel))
            }
            case Left(msg)  => BadRequest(msg)
          })
      },
      confirmData => {
        confirmData.confirm match {
          case Some("Yes") => {
            organisationService.removeMemberFromOrganisation(organisationId, userId)
              .map(_ match {
                case Right(org) => Redirect(routes.ManageMembersController.manageMembers(organisationId))
                case Left(msg)  => BadRequest(msg)
              })
          }
          case _           => successful(Redirect(routes.ManageMembersController.manageMembers(organisationId)))
        }
      }
    )
  }
}

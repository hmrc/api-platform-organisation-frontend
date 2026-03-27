/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformorganisationfrontend.models.views

import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationId
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationName
import uk.gov.hmrc.apiplatformorganisationfrontend.config.AppConfig
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes

case class Crumb(name: String, url: String = "", dataAttribute: Option[String] = None)

object Crumb {

  def home(implicit appConfig: AppConfig) =
    Crumb("Home", s"${appConfig.thirdPartyDeveloperFrontendUrl}/developer/applications", Some("data-breadcrumb-home"))

  def organisation(organisationId: OrganisationId, organisationName: OrganisationName) =
    Crumb(organisationName.value, routes.OrganisationController.organisationHomePage(organisationId).url, Some("data-breadcrumb-organisation-home"))

  def organisationMembers(organisationId: OrganisationId) =
    Crumb("Organisation members", routes.ManageMembersController.manageCollaborators(organisationId).url, Some("data-breadcrumb-organisation-members"))

}

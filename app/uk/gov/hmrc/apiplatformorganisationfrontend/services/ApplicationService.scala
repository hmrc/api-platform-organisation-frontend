/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformorganisationfrontend.services

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.GetAppsForAdminOrRIRequest
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands.LinkToOrganisation
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchRequest
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, LaxEmailAddress, OrganisationId}
import uk.gov.hmrc.apiplatform.modules.common.services.{ClockNow, EitherTHelper}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyOrchestratorConnector

@Singleton
class ApplicationService @Inject() (thirdPartyOrchestratorConnector: ThirdPartyOrchestratorConnector, val clock: Clock)(implicit val ec: ExecutionContext)
    extends EitherTHelper[String] with ClockNow {

  def getAppsForResponsibleIndividualOrAdmin(emailAddress: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[List[ApplicationWithCollaborators]] = {

    thirdPartyOrchestratorConnector.getAppsForResponsibleIndividualOrAdmin(GetAppsForAdminOrRIRequest(adminOrRespIndEmail = emailAddress))
  }

  def addOrgToApps(actor: Actors.AppCollaborator, organisationId: OrganisationId, applicationIds: Seq[String])(implicit hc: HeaderCarrier): Future[Unit] = {
    Future.sequence(
      applicationIds map {
        applicationId =>
          thirdPartyOrchestratorConnector.applicationCommandDispatch(
            applicationId,
            DispatchRequest(LinkToOrganisation(actor, organisationId, instant()), Set.empty)
          )
      }
    ).map(_ => ())
  }

}

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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationId
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{OrganisationConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.OrganisationWithMembers

@Singleton
class OrganisationService @Inject() (
    organisationConnector: OrganisationConnector,
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext
  ) extends EitherTHelper[String] {

  def fetch(id: OrganisationId)(implicit hc: HeaderCarrier): Future[Either[String, OrganisationWithMembers]] = {
    (
      for {
        org     <- fromOptionF(organisationConnector.fetchOrganisation(id), "Organisation not found")
        members <- liftF(thirdPartyDeveloperConnector.getRegisteredOrUnregisteredUsers(org.members.map(m => m.userId).toList))
      } yield OrganisationWithMembers(org, members.users)
    ).value
  }
}

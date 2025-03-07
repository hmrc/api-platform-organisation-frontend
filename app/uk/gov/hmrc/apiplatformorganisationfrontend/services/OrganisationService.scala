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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Organisation, OrganisationId}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{OrganisationConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.{OrganisationWithAllMembersDetails, OrganisationWithMemberDetails}

@Singleton
class OrganisationService @Inject() (
    organisationConnector: OrganisationConnector,
    thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector
  )(implicit val ec: ExecutionContext
  ) extends EitherTHelper[String] {

  def fetch(id: OrganisationId)(implicit hc: HeaderCarrier): Future[Option[Organisation]] = {
    organisationConnector.fetchOrganisation(id)
  }

  def fetchWithAllMembersDetails(id: OrganisationId)(implicit hc: HeaderCarrier): Future[Either[String, OrganisationWithAllMembersDetails]] = {
    (
      for {
        org     <- fromOptionF(organisationConnector.fetchOrganisation(id), "Organisation not found")
        members <- liftF(thirdPartyDeveloperConnector.getRegisteredOrUnregisteredUsers(org.members.map(m => m.userId).toList))
      } yield OrganisationWithAllMembersDetails(org, members.users)
    ).value
  }

  def fetchWithMemberDetails(id: OrganisationId, userId: UserId)(implicit hc: HeaderCarrier): Future[Either[String, OrganisationWithMemberDetails]] = {
    (
      for {
        org     <- fromOptionF(organisationConnector.fetchOrganisation(id), "Organisation not found")
        members <- liftF(thirdPartyDeveloperConnector.getRegisteredOrUnregisteredUsers(List(userId)))
        member  <- fromOption(members.users.find(m => m.userId == userId), "User not found")
      } yield OrganisationWithMemberDetails(org, member)
    ).value
  }

  def addMemberToOrganisation(id: OrganisationId, emailAddress: LaxEmailAddress)(implicit hc: HeaderCarrier): Future[Either[String, Organisation]] = {
    for {
      userId <- thirdPartyDeveloperConnector.getOrCreateUserId(emailAddress)
      org    <- organisationConnector.addMemberToOrganisation(id, userId)
    } yield org
  }

  def removeMemberFromOrganisation(id: OrganisationId, userId: UserId)(implicit hc: HeaderCarrier): Future[Either[String, Organisation]] = {
    organisationConnector.removeMemberFromOrganisation(id, userId)
  }
}

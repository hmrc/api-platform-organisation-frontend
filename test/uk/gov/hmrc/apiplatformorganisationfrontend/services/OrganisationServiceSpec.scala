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

package uk.gov.hmrc.apiplatformorganisationfrontend.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.Collaborator.Roles
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Collaborators, Organisation, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.{GetRegisteredOrUnregisteredUsersResponse, RegisteredOrUnregisteredUser}
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.OrganisationConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.models.{CollaboratorWithUserDetails, OrganisationWithAllMembersDetails}

class OrganisationServiceSpec extends AsyncHmrcSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  trait Setup extends FixedClock with ThirdPartyDeveloperConnectorMockModule with LocalUserIdTracker {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val orgId             = OrganisationId.random
    val userId            = UserId.random
    val email             = LaxEmailAddress("bob@example.com")
    val organisation      = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Collaborators.Member(userId)))
    val userRegDetails    = RegisteredOrUnregisteredUser(userId, email, true, true)
    val userDetails       = User(email, "Bob", "Fleming", instant, instant, true, None, List.empty, None, EmailPreferences.noPreferences, userId)
    val orgWithAllMembers = OrganisationWithAllMembersDetails(organisation, Set(CollaboratorWithUserDetails(Collaborators.Member(userId), userRegDetails, Some(userDetails))))

    val mockOrganisationConnector = mock[OrganisationConnector]

    val underTest = new OrganisationService(
      mockOrganisationConnector,
      ThirdPartyDeveloperConnectorMock.aMock
    )
  }

  "fetch" should {
    "return organisation for given org id" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(Some(organisation)))

      val result = await(underTest.fetch(orgId))

      result shouldBe defined
      result.get.id shouldBe orgId
    }
  }

  "fetchWithAllMembersDetails" should {
    "return organisation with members details for given org id" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(Some(organisation)))
      ThirdPartyDeveloperConnectorMock.GetRegisteredOrUnregisteredUsers.succeeds(GetRegisteredOrUnregisteredUsersResponse(List(userRegDetails)))
      ThirdPartyDeveloperConnectorMock.FetchDevelopers.succeeds(List(userDetails))

      val result = await(underTest.fetchWithAllMembersDetails(orgId))

      result.isRight shouldBe true
      result.value.organisation.id shouldBe orgId
      result.value.collaborators.head.user.email shouldBe email
    }

    "return left if organisation not found" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(None))

      val result = await(underTest.fetchWithAllMembersDetails(orgId))

      result.isLeft shouldBe true
      result.left.value shouldBe "Organisation not found"
    }
  }

  "fetchWithMemberDetails" should {
    "return organisation with members details for given org id" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(Some(organisation)))
      ThirdPartyDeveloperConnectorMock.GetRegisteredOrUnregisteredUsers.succeeds(GetRegisteredOrUnregisteredUsersResponse(List(userRegDetails)))
      ThirdPartyDeveloperConnectorMock.FetchDeveloper.succeeds(user)

      val result = await(underTest.fetchWithMemberDetails(orgId, userId))

      result.isRight shouldBe true
      result.value.organisation.id shouldBe orgId
      result.value.collaborator.user.email shouldBe email
      result.value.collaborator.maybeUserDetails.isDefined shouldBe true
      result.value.collaborator.maybeUserDetails.get.firstName shouldBe "John"
    }

    "return organisation with members details but no user details for unregistered user" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(Some(organisation)))
      ThirdPartyDeveloperConnectorMock.GetRegisteredOrUnregisteredUsers.succeeds(GetRegisteredOrUnregisteredUsersResponse(List(userRegDetails)))
      ThirdPartyDeveloperConnectorMock.FetchDeveloper.returnsNone()

      val result = await(underTest.fetchWithMemberDetails(orgId, userId))

      result.isRight shouldBe true
      result.value.organisation.id shouldBe orgId
      result.value.collaborator.user.email shouldBe email
      result.value.collaborator.maybeUserDetails.isDefined shouldBe false
    }

    "return left if organisation not found" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(None))

      val result = await(underTest.fetchWithMemberDetails(orgId, userId))

      result.isLeft shouldBe true
      result.left.value shouldBe "Organisation not found"
    }

    "return left if user not found" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(Some(organisation)))
      ThirdPartyDeveloperConnectorMock.GetRegisteredOrUnregisteredUsers.succeeds(GetRegisteredOrUnregisteredUsersResponse(List.empty))

      val result = await(underTest.fetchWithMemberDetails(orgId, userId))

      result.isLeft shouldBe true
      result.left.value shouldBe "User not found"
    }
  }

  "addCollaboratorToOrganisation" should {
    "get or create user in TPD, then call back end connector to add user id to org" in new Setup {
      when(mockOrganisationConnector.addCollaboratorToOrganisation(*[OrganisationId], *[LaxEmailAddress], *)(*)).thenReturn(successful(Right(organisation)))

      val result = await(underTest.addCollaboratorToOrganisation(orgId, email, Roles.Member))

      result.isRight shouldBe true
      result.value.id shouldBe orgId
    }

    "return left if fails to add new member" in new Setup {
      when(mockOrganisationConnector.addCollaboratorToOrganisation(*[OrganisationId], *[LaxEmailAddress], *)(*)).thenReturn(successful(
        Left(s"Failed to add user $userId to organisation $orgId")
      ))

      val result = await(underTest.addCollaboratorToOrganisation(orgId, email, Roles.Member))

      result.isLeft shouldBe true
      result.left.value shouldBe s"Failed to add user $userId to organisation $orgId"
    }
  }

  "removeCollaboratorFromOrganisation" should {
    "call back end connector to remove user id from org" in new Setup {
      when(mockOrganisationConnector.removeCollaboratorFromOrganisation(*[OrganisationId], *[UserId], *[LaxEmailAddress])(*)).thenReturn(successful(Right(organisation)))

      val result = await(underTest.removeCollaboratorFromOrganisation(orgId, userId, email))

      result.isRight shouldBe true
      result.value.id shouldBe orgId
    }

    "return left if fails to remove member" in new Setup {
      when(mockOrganisationConnector.removeCollaboratorFromOrganisation(*[OrganisationId], *[UserId], *[LaxEmailAddress])(*)).thenReturn(successful(
        Left(s"Failed to remove user $userId from organisation $orgId")
      ))

      val result = await(underTest.removeCollaboratorFromOrganisation(orgId, userId, email))

      result.isLeft shouldBe true
      result.left.value shouldBe s"Failed to remove user $userId from organisation $orgId"
    }
  }
}

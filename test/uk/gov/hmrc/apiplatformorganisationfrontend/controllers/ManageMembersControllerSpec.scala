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

import scala.concurrent.ExecutionContext

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import play.api.test.{CSRFTokenHelper, FakeRequest}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, OrganisationId, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Collaborators, Organisation, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.RegisteredOrUnregisteredUser
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.{OrganisationActionServiceMockModule, OrganisationServiceMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.{CollaboratorWithUserDetails, OrganisationWithAllMembersDetails, OrganisationWithMemberDetails}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

class ManageMembersControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
    with OrganisationServiceMockModule
    with ThirdPartyDeveloperConnectorMockModule
    with UserBuilder
    with LocalUserIdTracker
    with FixedClock {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends OrganisationServiceMockModule
      with OrganisationActionServiceMockModule
      with ThirdPartyDeveloperConnectorMockModule
      with LocalUserIdTracker {

    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val manageMembersPage             = app.injector.instanceOf[ManageMembersPage]
    val manageMemberPage              = app.injector.instanceOf[ManageMemberPage]
    val addMemberPage                 = app.injector.instanceOf[AddMemberPage]
    val removeMemberPage              = app.injector.instanceOf[RemoveMemberPage]
    val addMemberSuccessPage          = app.injector.instanceOf[AddMemberSuccessPage]
    val removeMemberSuccessPage       = app.injector.instanceOf[RemoveMemberSuccessPage]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val underTest =
      new ManageMembersController(
        mcc,
        manageMembersPage,
        manageMemberPage,
        addMemberPage,
        removeMemberPage,
        addMemberSuccessPage,
        removeMemberSuccessPage,
        OrganisationServiceMock.aMock,
        OrganisationActionServiceMock.aMock,
        cookieSigner,
        errorHandler,
        ThirdPartyDeveloperConnectorMock.aMock
      )

    val orgId        = OrganisationId.random
    val userId       = userSession.developer.userId
    val userId2      = UserId.random
    val email        = LaxEmailAddress("bob@example.com")
    val email2       = LaxEmailAddress("bill@example.com")
    val organisation = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Collaborators.Member(userId)))

    val orgWithAllMembers =
      OrganisationWithAllMembersDetails(
        organisation,
        Set(
          CollaboratorWithUserDetails(Collaborators.Member(userId), RegisteredOrUnregisteredUser(userId, email, true, true), Some(user)),
          CollaboratorWithUserDetails(Collaborators.Member(userId2), RegisteredOrUnregisteredUser(userId2, email2, false, false), None)
        )
      )

    val orgWithNoUnverifiedMembers =
      OrganisationWithAllMembersDetails(
        organisation,
        Set(
          CollaboratorWithUserDetails(Collaborators.Member(userId), RegisteredOrUnregisteredUser(userId, email, true, true), Some(user))
        )
      )

    val orgWithMember =
      OrganisationWithMemberDetails(
        organisation,
        CollaboratorWithUserDetails(Collaborators.Member(userId), RegisteredOrUnregisteredUser(userId, email, true, true), Some(user))
      )

    val orgWithUnregisteredMember =
      OrganisationWithMemberDetails(
        organisation,
        CollaboratorWithUserDetails(Collaborators.Member(userId), RegisteredOrUnregisteredUser(userId, email, false, false), None)
      )

    implicit val loggedInUser: User = user
  }

  "GET /manage-collaborators" should {
    "return page with list of verified and unverified members" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborators").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithAllMembersDetails.thenReturns(orgWithAllMembers)

      val result = underTest.manageCollaborators(orgId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Manage organisation members")
      contentAsString(result) should include("Invites awaiting a response")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("(Unverified)")
      contentAsString(result) should include("bob@example.com")
      contentAsString(result) should include("bill@example.com")
    }

    "return page with list of verified members only" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborators").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithAllMembersDetails.thenReturns(orgWithNoUnverifiedMembers)

      val result = underTest.manageCollaborators(orgId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Manage organisation members")
      contentAsString(result) should include("Invites awaiting a response")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("bob@example.com")
      contentAsString(result) should include("Your organisation does not have any invites awaiting a response.")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborators").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithAllMembersDetails.thenReturnsNone()

      val result = underTest.manageCollaborators(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return not authorised if not a member of the organisation" in new Setup {
      val orgNotMember = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Collaborators.Member(UserId.random)))
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(orgNotMember, userSession)
      val fakeRequest  = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborators").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithAllMembersDetails.thenReturns(orgWithAllMembers)

      val result = underTest.manageCollaborators(orgId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId")
    }
  }

  "GET /manage-collaborator" should {
    "return page with details of member" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturns(orgWithMember)

      val result = underTest.manageCollaborator(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("John Doe")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Remove this user from the organisation")
      contentAsString(result) should include("bob@example.com")
    }

    "return page with details of unregistered member" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturns(orgWithUnregisteredMember)

      val result = underTest.manageCollaborator(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("(Unverified)")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Remove this user from the organisation")
      contentAsString(result) should include("bob@example.com")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturnsNone()

      val result = underTest.manageCollaborator(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return not authorised if not a member of the organisation" in new Setup {
      val orgNotMember = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Collaborators.Member(UserId.random)))
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(orgNotMember, userSession)
      val fakeRequest  = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturns(orgWithMember)

      val result = underTest.manageCollaborator(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId")
    }
  }

  "GET /add-collaborator" should {
    "return page to add new member's email address" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/add-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addCollaborator(orgId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Email address")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/add-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturnsNone()

      val result = underTest.addCollaborator(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "POST /add-collaborator" should {
    "add new member and redirect to manage members page" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "john@example.com", "role" -> "member"))
      OrganisationServiceMock.AddMemberToOrganisation.thenReturns(organisation)

      val result = underTest.addCollaboratorAction(orgId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId/add-collaborator-success?role=Member")
    }

    "return bad request if no email address entered" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "", "role" -> "member"))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addCollaboratorAction(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Enter an email address")
    }

    "return bad request if no role selected" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "john@example.com", "role" -> ""))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addCollaboratorAction(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Please select an option")
    }

    "return bad request if invalid email address entered" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "fshdgfskjf", "role" -> "member"))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addCollaboratorAction(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Provide a valid email address")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "john@example.com", "role" -> "member"))
      OrganisationServiceMock.AddMemberToOrganisation.thenReturnsNone()

      val result = underTest.addCollaboratorAction(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "GET /add-collaborator-success" should {
    "return page to confirm new member added" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/add-collaborator-success").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addCollaboratorSuccess(orgId, "Member")(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Organisation member added")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("We've sent an email to tell them that they have been added as a member to your organisation.")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/add-collaborator-success").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturnsNone()

      val result = underTest.addCollaboratorSuccess(orgId, "Member")(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "GET /remove-collaborator" should {
    "return page to remove existing member" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/remove-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturns(orgWithMember)

      val result = underTest.removeCollaborator(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Are you sure that you want to remove this user from your organisation?")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("bob@example.com")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/remove-collaborator").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturnsNone()

      val result = underTest.removeCollaborator(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "POST /remove-collaborator" should {
    "remove existing member and redirect to manage members page" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/remove-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("confirm" -> "Yes", "role" -> "Member", "email" -> "bob@example.com"))
      OrganisationServiceMock.RemoveMemberFromOrganisation.thenReturns(organisation)

      val result = underTest.removeCollaboratorAction(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId/remove-collaborator-success?role=Member")
    }

    "do not remove existing member if user selects No" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/remove-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("confirm" -> "No", "role" -> "Member", "email" -> "bob@example.com"))

      val result = underTest.removeCollaboratorAction(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId/manage-collaborators")
    }

    "return bad request if user doesn't select Yes or No" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/remove-collaborator")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("confirm" -> ""))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturns(orgWithMember)

      val result = underTest.removeCollaboratorAction(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Are you sure that you want to remove this user from your organisation?")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("bob@example.com")
      contentAsString(result) should include("Please select an option")
    }
  }

  "GET /remove-collaborator-success" should {
    "return page to confirm member removed" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/remove-collaborator-success").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.removeCollaboratorSuccess(orgId, "Member")(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Organisation member removed")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("We've sent an email to tell them that they have been removed as a member from your organisation.")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationActionServiceMock.givenOrganisationAction(organisation, userSession)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/remove-collaborator-success").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturnsNone()

      val result = underTest.removeCollaboratorSuccess(orgId, "Member")(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }
}

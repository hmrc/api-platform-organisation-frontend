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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationId, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.RegisteredOrUnregisteredUser
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.OrganisationServiceMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.models.{OrganisationWithAllMembersDetails, OrganisationWithMemberDetails}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

class ManageMembersControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
    with OrganisationServiceMockModule
    with ThirdPartyDeveloperConnectorMockModule
    with UserBuilder
    with LocalUserIdTracker {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends OrganisationServiceMockModule
      with ThirdPartyDeveloperConnectorMockModule
      with LocalUserIdTracker {

    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val manageMembersPage             = app.injector.instanceOf[ManageMembersPage]
    val addMemberPage                 = app.injector.instanceOf[AddMemberPage]
    val removeMemberPage              = app.injector.instanceOf[RemoveMemberPage]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val underTest =
      new ManageMembersController(
        mcc,
        manageMembersPage,
        addMemberPage,
        removeMemberPage,
        OrganisationServiceMock.aMock,
        cookieSigner,
        errorHandler,
        ThirdPartyDeveloperConnectorMock.aMock
      )

    val orgId             = OrganisationId.random
    val userId            = UserId.random
    val email             = LaxEmailAddress("bob@example.com")
    val organisation      = Organisation(orgId, OrganisationName("My org"), Set(Member(userId)))
    val orgWithAllMembers = OrganisationWithAllMembersDetails(organisation, List(RegisteredOrUnregisteredUser(userId, email, true, true)))
    val orgWithMember     = OrganisationWithMemberDetails(organisation, RegisteredOrUnregisteredUser(userId, email, true, true))

    implicit val loggedInUser: User = user
  }

  "GET /manage-members" should {
    "return page with list of members" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-members").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithAllMembersDetails.thenReturns(orgWithAllMembers)

      val result = underTest.manageMembers(orgId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Manage organisation members")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("bob@example.com")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/manage-members").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithAllMembersDetails.thenReturnsNone()

      val result = underTest.manageMembers(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "GET /add-member" should {
    "return page to add new member's email address" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/add-member").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addMember(orgId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Email address")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/add-member").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturnsNone()

      val result = underTest.addMember(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "POST /add-member" should {
    "add new member and redirect to manage members page" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-member")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "john@example.com"))
      OrganisationServiceMock.AddMemberToOrganisation.thenReturns(organisation)

      val result = underTest.addMemberAction(orgId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId/manage-members")
    }

    "return bad request if no email address entered" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-member")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> ""))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addMemberAction(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Enter an email address")
    }

    "return bad request if invalid email address entered" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-member")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "fshdgfskjf"))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.addMemberAction(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Add an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Provide a valid email address")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-member")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("email" -> "john@example.com"))
      OrganisationServiceMock.AddMemberToOrganisation.thenReturnsNone()

      val result = underTest.addMemberAction(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "GET /remove-member" should {
    "return page to remove existing member" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/remove-member").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturns(orgWithMember)

      val result = underTest.removeMember(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Remove an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("bob@example.com")
    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/remove-member").withUser(underTest)(sessionId))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturnsNone()

      val result = underTest.removeMember(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "POST /remove-member" should {
    "remove existing member and redirect to manage members page" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-member")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("confirm" -> "Yes", "email" -> "bob@example.com"))
      OrganisationServiceMock.RemoveMemberFromOrganisation.thenReturns(organisation)

      val result = underTest.removeMemberAction(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId/manage-members")
    }

    "do not remove existing member if user selects No" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-member")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("confirm" -> "No", "email" -> "bob@example.com"))

      val result = underTest.removeMemberAction(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/organisation/$orgId/manage-members")
    }

    "return bad request if user doesn't select Yes or No" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/add-member")
        .withUser(underTest)(sessionId)
        .withFormUrlEncodedBody("confirm" -> ""))
      OrganisationServiceMock.FetchWithMemberDetails.thenReturns(orgWithMember)

      val result = underTest.removeMemberAction(orgId, userId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Remove an organisation member")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("bob@example.com")
      contentAsString(result) should include("Please select an option")
    }
  }
}

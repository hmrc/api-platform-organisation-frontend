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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationId
import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.OrganisationConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.{OrganisationActionServiceMockModule, OrganisationServiceMockModule, SubmissionServiceMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

class OrganisationControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
    with SubmissionServiceMockModule
    with ThirdPartyDeveloperConnectorMockModule
    with OrganisationServiceMockModule
    with UserBuilder
    with LocalUserIdTracker
    with SubmissionsTestData {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends SubmissionServiceMockModule
      with SubmissionsTestData
      with ThirdPartyDeveloperConnectorMockModule
      with OrganisationActionServiceMockModule
      with LocalUserIdTracker {

    val mcc                            = app.injector.instanceOf[MessagesControllerComponents]
    val beforeYouStartPage             = app.injector.instanceOf[BeforeYouStartPage]
    val checkResponsibleIndividualPage = app.injector.instanceOf[CheckResponsibleIndividualPage]
    val mainLandingPage                = app.injector.instanceOf[LandingPage]
    val organisationHomePage           = app.injector.instanceOf[OrganisationHomePage]
    val cookieSigner                   = app.injector.instanceOf[CookieSigner]
    val errorHandler                   = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig  = app.injector.instanceOf[AppConfig]

    val mockOrganisationConnector = mock[OrganisationConnector]

    val underTest =
      new OrganisationController(
        mcc,
        beforeYouStartPage,
        checkResponsibleIndividualPage,
        mainLandingPage,
        organisationHomePage,
        SubmissionServiceMock.aMock,
        OrganisationServiceMock.aMock,
        mockOrganisationConnector,
        OrganisationActionServiceMock.aMock,
        cookieSigner,
        errorHandler,
        ThirdPartyDeveloperConnectorMock.aMock
      )

    implicit val loggedInUser: User = user

    val orgId        = OrganisationId.random
    val organisation = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Member(userId)))
  }

  "GET /before-you-start" should {
    "return 200" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/before-you-start").withUser(underTest)(sessionId))

      val result = underTest.beforeYouStartView(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/before-you-start").withUser(underTest)(sessionId))

      val result = underTest.beforeYouStartView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Set up the Developer Hub dashboard for your business")
    }

    "returns 303 on logged out" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.fails()
      val loggedOutRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/landing").withUser(underTest)(sessionId))

      val result = underTest.beforeYouStartView(loggedOutRequest)
      status(result) shouldBe Status.SEE_OTHER
    }
  }

  "POST /before-you-start" should {
    "return 303" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/before-you-start").withUser(underTest)(sessionId))

      val result = underTest.beforeYouStartAction(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/check-responsible-individual")
    }

  }

  "GET /landing" should {
    "return landing page with button to continue submission if submission for user found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/landing").withUser(underTest)(sessionId))
      SubmissionServiceMock.FetchLatestSubmissionByUserId.thenReturns(aSubmission)

      val result = underTest.landingView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Main organisations landing page")
      contentAsString(result) should include("Continue Organisation entry")
    }

    "return landing page with button to create new submission if submission for user not found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/landing").withUser(underTest)(sessionId))
      SubmissionServiceMock.FetchLatestSubmissionByUserId.thenReturnsNone()

      val result = underTest.landingView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Main organisations landing page")
      contentAsString(result) should include("Create New Organisation")
    }
  }

  "GET /organisation/oid" should {
    "return the organisation home page" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/organisation/1").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturns(organisation)

      val result = underTest.organisationHomePage(orgId)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include(s"${user.displayedName}")
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include("Home")
      contentAsString(result) should include("Organisation home")
      contentAsString(result) should include("My org")
      contentAsString(result) should include("Add your applications")
      contentAsString(result) should include("View your organisation's sandbox and production applications.")
      contentAsString(result) should include("Team member access")
      contentAsString(result) should include("Manage your organisation's team members.")

    }

    "return bad request if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/organisation/1").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturnsNone()

      val result = underTest.organisationHomePage(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }
}

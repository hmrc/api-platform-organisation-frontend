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

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

class OrganisationControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
    with SubmissionServiceMockModule
    with ThirdPartyDeveloperConnectorMockModule
    with UserBuilder
    with LocalUserIdTracker
    with SubmissionsTestData {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends SubmissionServiceMockModule
      with SubmissionsTestData
      with ThirdPartyDeveloperConnectorMockModule
      with LocalUserIdTracker {

    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val landingPage                   = app.injector.instanceOf[OrganisationLandingPage]
    val mainLandingPage               = app.injector.instanceOf[MainLandingPage]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val underTest =
      new OrganisationController(
        mcc,
        landingPage,
        mainLandingPage,
        SubmissionServiceMock.aMock,
        cookieSigner,
        errorHandler,
        ThirdPartyDeveloperConnectorMock.aMock
      )

    implicit val loggedInUser = user

  }

  "GET /landing" should {
    "return 200" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/landing").withUser(underTest)(sessionId))

      val result = underTest.organisationLandingView(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/landing").withUser(underTest)(sessionId))

      val result = underTest.organisationLandingView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Get verified on the Developer Hub")
    }

    "returns 303 on logged out" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.fails()
      val loggedOutRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/landing").withUser(underTest)(sessionId))

      val result = underTest.organisationLandingView(loggedOutRequest)
      status(result) shouldBe Status.SEE_OTHER
    }
  }

  "POST /landing" should {
    "return 303" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/landing").withUser(underTest)(sessionId))
      SubmissionServiceMock.CreateSubmission.thenReturns(aSubmission)

      val result = underTest.organisationLandingAction(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${aSubmission.id.value}/checklist")
    }

    "return 400 if fails" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/landing").withUser(underTest)(sessionId))
      SubmissionServiceMock.CreateSubmission.thenReturnsNone()

      val result = underTest.organisationLandingAction(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "GET /main-landing" should {
    "return landing page with button to continue submission if submission for user found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/landing").withUser(underTest)(sessionId))
      SubmissionServiceMock.FetchLatestSubmissionByUserId.thenReturns(aSubmission)

      val result = underTest.mainLandingView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Main organisations landing page")
      contentAsString(result) should include("Continue Organisation entry")
    }

    "return landing page with button to create new submission if submission for user not found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/landing").withUser(underTest)(sessionId))
      SubmissionServiceMock.FetchLatestSubmissionByUserId.thenReturnsNone()

      val result = underTest.mainLandingView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Main organisations landing page")
      contentAsString(result) should include("Create New Organisation")
    }
  }
}

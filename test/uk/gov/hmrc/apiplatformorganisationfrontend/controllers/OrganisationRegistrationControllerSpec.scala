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
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Collaborators, Organisation, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.OrganisationAllowList
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

class OrganisationRegistrationControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
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
    val registrationStartPage          = app.injector.instanceOf[RegistrationStartPage]
    val checkResponsibleIndividualPage = app.injector.instanceOf[CheckResponsibleIndividualPage]
    val notResponsibleIndividualPage   = app.injector.instanceOf[NotResponsibleIndividualPage]
    val notAllowListedPage             = app.injector.instanceOf[NotAllowListedPage]
    val cookieSigner                   = app.injector.instanceOf[CookieSigner]
    val errorHandler                   = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig  = app.injector.instanceOf[AppConfig]

    val mockOrganisationConnector = mock[OrganisationConnector]

    val underTest =
      new OrganisationRegistrationController(
        mcc,
        registrationStartPage,
        checkResponsibleIndividualPage,
        notResponsibleIndividualPage,
        notAllowListedPage,
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
    val organisation = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Collaborators.Member(userId)))
    val allowList    = OrganisationAllowList(userId, OrganisationName("My Org 1"), "requestedBy", instant)
  }

  "GET /registration" should {
    "show registration start page" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      SubmissionServiceMock.FetchLatestSubmissionByUserId.thenReturnsNone()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/registration").withUser(underTest)(sessionId))

      val result = underTest.registrationStartView()(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Set up the Developer Hub dashboard for your business")
    }

    "redirect to check list page if you already have a submission" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      SubmissionServiceMock.FetchLatestSubmissionByUserId.thenReturns(aSubmission)
      val loggedOutRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/registration").withUser(underTest)(sessionId))

      val result = underTest.registrationStartView()(loggedOutRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${aSubmission.id}/checklist")
    }

    "redirect to not allow listed page if you are not in the allow list" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturnsNone()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/registration").withUser(underTest)(sessionId))

      val result = underTest.registrationStartView()(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/registration/not-allow-listed")
    }

    "redirect to login when logged out" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.fails()
      val loggedOutRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/registration").withUser(underTest)(sessionId))

      val result = underTest.registrationStartView()(loggedOutRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("http://localhost:9685/developer/login")
    }
  }

  "GET /not-allow-listed" should {
    "return 200" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/registration/not-allow-listed").withUser(underTest)(sessionId))

      val result = underTest.notAllowListedView(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsString(result) should include("You are not authorised to complete these checks yet")
    }
  }

  "POST /registration" should {
    "return 303" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/registration").withUser(underTest)(sessionId))

      val result = underTest.registrationStartAction()(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/registration/check-responsible-individual")
    }
  }

  "GET /check-responsible-individual" should {
    "return 200" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/registration/check-responsible-individual").withUser(underTest)(sessionId))

      val result = underTest.checkResponsibleIndividualView(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsString(result) should include(
        "Are you the person responsible for making sure that software products owned by the business you work for comply with our API terms of use?"
      )
    }
  }

  "POST /check-responsible-individual" should {
    "create new submission and redirect to it if user selected Yes" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", "/registration/check-responsible-individual").withUser(underTest)(sessionId).withFormUrlEncodedBody("confirmResponsibleIndividual" -> "yes")
      )
      SubmissionServiceMock.CreateSubmission.thenReturns(aSubmission)

      val result = underTest.checkResponsibleIndividualAction()(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/submission/${aSubmission.id.value}/checklist")
      SubmissionServiceMock.CreateSubmission.verifyCalledWith(loggedInUser.userId, loggedInUser.email)
    }

    "redirect to 'no' page if user selected No" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", "/registration/check-responsible-individual").withUser(underTest)(sessionId).withFormUrlEncodedBody("confirmResponsibleIndividual" -> "no")
      )

      val result = underTest.checkResponsibleIndividualAction()(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/api-platform-organisation/registration/not-responsible-individual")
      SubmissionServiceMock.CreateSubmission.verifyNotCalled()
    }

    "return bad request if no option selected" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", "/registration/check-responsible-individual").withUser(underTest)(sessionId)
      )

      val result = underTest.checkResponsibleIndividualAction()(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) should include("Please select an option")
      SubmissionServiceMock.CreateSubmission.verifyNotCalled()
    }

    "redirect to not allow listed page if you are not in the allow list" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturnsNone()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", "/registration/check-responsible-individual").withUser(underTest)(sessionId).withFormUrlEncodedBody("confirmResponsibleIndividual" -> "no")
      )

      val result = underTest.checkResponsibleIndividualAction()(fakeRequest)
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("/api-platform-organisation/registration/not-allow-listed")
      SubmissionServiceMock.CreateSubmission.verifyNotCalled()
    }
  }

  "GET /not-responsible-individual" should {
    "return 200" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      SubmissionServiceMock.FetchAllowList.thenReturns(allowList)
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/registration/not-responsible-individual").withUser(underTest)(sessionId))

      val result = underTest.notResponsibleIndividualView(fakeRequest)
      status(result) shouldBe Status.OK
      contentAsString(result) should include("Ask someone else to complete these checks instead")
    }
  }
}

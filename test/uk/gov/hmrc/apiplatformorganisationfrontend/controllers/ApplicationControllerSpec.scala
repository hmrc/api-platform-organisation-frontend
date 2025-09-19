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
import uk.gov.hmrc.http.InternalServerException

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationIdFixtures
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.{ApplicationServiceMockModule, OrganisationServiceMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.application.AddApplicationsView

class ApplicationControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
    with ThirdPartyDeveloperConnectorMockModule
    with UserBuilder
    with LocalUserIdTracker
    with OrganisationIdFixtures
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends ThirdPartyDeveloperConnectorMockModule
      with OrganisationServiceMockModule
      with ApplicationServiceMockModule
      with LocalUserIdTracker {

    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val addApplicationsView           = app.injector.instanceOf[AddApplicationsView]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val underTest =
      new ApplicationController(
        mcc,
        ApplicationServiceMock.aMock,
        OrganisationServiceMock.aMock,
        addApplicationsView,
        ThirdPartyDeveloperConnectorMock.aMock,
        errorHandler,
        cookieSigner
      )

    val organisation = Organisation(organisationIdOne, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Member(user.userId)))

    implicit val loggedInUser: User = user

  }

  "GET /organisation/:oid/add-applications" should {
    "return 200" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturns(organisation)
      ApplicationServiceMock.GetAppsForResponsibleIndividualOrAdmin.thenReturns(List(standardApp, standardApp2))
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId))

      val result = underTest.addApplications(organisationIdOne)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include(s"Which of these software applications does ${organisation.organisationName.value} own?")
    }

    "return 400 if no organisation found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturnsNone()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId))

      val result = underTest.addApplications(organisationIdOne)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 500 if GetAppsForResponsibleIndividualOrAdmin throws an exception" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturns(organisation)
      ApplicationServiceMock.GetAppsForResponsibleIndividualOrAdmin.thenThrowsException()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId))

      intercept[InternalServerException] {
        await(underTest.addApplications(organisationIdOne)(fakeRequest))
      }.responseCode shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "POST /organisation/:oid/add-applications" should {
    "return 200 when form has no errors and one application has been selected" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturns(organisation)
      ApplicationServiceMock.GetAppsForResponsibleIndividualOrAdmin.thenReturns(List(standardApp, standardApp2))
      ApplicationServiceMock.AddOrgToApps.thenReturnsSuccess()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId)
          .withFormUrlEncodedBody("selectedPrincipalApps[0]" -> s"${standardApp.id.toString}")
      )

      val result = underTest.addApplicationsAction(organisationIdOne)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/plain")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include(s"was added to the following principal apps")
    }

    "return 200 when form has no errors and two applications has been selected" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturns(organisation)
      ApplicationServiceMock.GetAppsForResponsibleIndividualOrAdmin.thenReturns(List(standardApp, standardApp2))
      ApplicationServiceMock.AddOrgToApps.thenReturnsSuccess()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId)
          .withFormUrlEncodedBody("selectedPrincipalApps[0]" -> s"${standardApp.id.toString}", "selectedSubordinateApps[0]" -> s"${standardApp2.id.toString}")
      )

      val result = underTest.addApplicationsAction(organisationIdOne)(fakeRequest)
      status(result) shouldBe Status.OK
      contentType(result) shouldBe Some("text/plain")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include(s"was added to the following principal apps")
      contentAsString(result) should include(s"and subordinate apps")
    }

    "return 400 when form has errors" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturns(organisation)
      ApplicationServiceMock.GetAppsForResponsibleIndividualOrAdmin.thenReturns(List(standardApp, standardApp2))
      ApplicationServiceMock.AddOrgToApps.thenReturnsSuccess()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId)
      )

      val result = underTest.addApplicationsAction(organisationIdOne)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include(s"You must select at least one application")
    }

    "return 400 when form has errors and org not found" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturnsNone
      ApplicationServiceMock.GetAppsForResponsibleIndividualOrAdmin.thenReturns(List(standardApp, standardApp2))
      ApplicationServiceMock.AddOrgToApps.thenReturnsSuccess()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId)
      )

      val result = underTest.addApplicationsAction(organisationIdOne)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
      contentType(result) shouldBe Some("text/plain")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include(s"Organisation not found")
    }

    "throws exception when valid form and add to apps fails" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      OrganisationServiceMock.Fetch.thenReturns(organisation)
      ApplicationServiceMock.GetAppsForResponsibleIndividualOrAdmin.thenReturns(List(standardApp, standardApp2))
      ApplicationServiceMock.AddOrgToApps.thenThrowsException()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(
        FakeRequest("POST", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId)
          .withFormUrlEncodedBody("selectedPrincipalApps[0]" -> s"${standardApp.id.toString}")
      )

      intercept[InternalServerException] {
        await(underTest.addApplicationsAction(organisationIdOne)(fakeRequest))
      }.responseCode shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }
}

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
import uk.gov.hmrc.apiplatformorganisationfrontend.OrganisationFixtures
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.OrganisationServiceMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html._

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker

class OrganisationControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
    with OrganisationServiceMockModule
    with ThirdPartyDeveloperConnectorMockModule
    with UserBuilder
    with LocalUserIdTracker
    with OrganisationFixtures {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()
  private val mcc                             = app.injector.instanceOf[MessagesControllerComponents]
  private val createPage                      = app.injector.instanceOf[CreateOrganisationPage]
  private val landingPage                     = app.injector.instanceOf[OrganisationLandingPage]
  private val successPage                     = app.injector.instanceOf[CreateOrganisationSuccessPage]
  private val cookieSigner                    = app.injector.instanceOf[CookieSigner]
  private val errorHandler                    = app.injector.instanceOf[ErrorHandler]
  private implicit val appConfig: AppConfig   = app.injector.instanceOf[AppConfig]

  private val underTest =
    new OrganisationController(mcc, createPage, successPage, landingPage, OrganisationServiceMock.aMock, cookieSigner, errorHandler, ThirdPartyDeveloperConnectorMock.aMock)

  "GET /create" should {
    val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/"))

    "return 200" in {
      val result = underTest.createOrganisationView(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in {
      val result = underTest.createOrganisationView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }

  "POST /create" should {
    val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/create").withFormUrlEncodedBody("organisation-name" -> "1234"))

    "return 200" in {
      OrganisationServiceMock.CreateOrganisation.willReturn(standardOrg)
      val result = underTest.createOrganisationAction(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return 400" in {
      val result = underTest.createOrganisationAction(
        CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/create").withFormUrlEncodedBody("organisation-name" -> ""))
      )
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return HTML" in {
      OrganisationServiceMock.CreateOrganisation.willReturn(standardOrg)

      val result = underTest.createOrganisationAction(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }

  "GET /landing" should {
    ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
    val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/landing").withUser(underTest)(sessionId))

    "return 200" in {
      val result = underTest.organisationLandingView(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in {
      val result = underTest.organisationLandingView(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
      contentAsString(result) should include("Get verified on the Developer Hub")
    }

    "returns 303 on logged out" in {
      ThirdPartyDeveloperConnectorMock.FetchSession.fails()
      val loggedOutRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", "/landing").withUser(underTest)(sessionId))

      val result = underTest.organisationLandingView(loggedOutRequest)
      status(result) shouldBe Status.SEE_OTHER
    }
  }

}

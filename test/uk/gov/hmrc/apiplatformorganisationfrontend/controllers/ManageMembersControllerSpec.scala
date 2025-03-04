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
import uk.gov.hmrc.apiplatformorganisationfrontend.models.OrganisationWithMembers
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
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val underTest =
      new ManageMembersController(
        mcc,
        manageMembersPage,
        OrganisationServiceMock.aMock,
        cookieSigner,
        errorHandler,
        ThirdPartyDeveloperConnectorMock.aMock
      )

    val orgId          = OrganisationId.random
    val userId         = UserId.random
    val email          = LaxEmailAddress("bob@example.com")
    val organisation   = Organisation(orgId, OrganisationName("My org"), Set(Member(userId, email)))
    val orgWithMembers = OrganisationWithMembers(organisation, List(RegisteredOrUnregisteredUser(userId, email, true, true)))

    implicit val loggedInUser: User = user
  }

  "GET /manage-members" should {
    "return page with list of members" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/manage-members").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturns(orgWithMembers)

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
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("POST", "/manage-members").withUser(underTest)(sessionId))
      OrganisationServiceMock.Fetch.thenReturnsNone()

      val result = underTest.manageMembers(orgId)(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

  }
}

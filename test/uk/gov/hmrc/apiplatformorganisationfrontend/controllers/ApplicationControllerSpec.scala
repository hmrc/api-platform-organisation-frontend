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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import play.api.test.{CSRFTokenHelper, FakeRequest}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationIdFixtures
import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.services.{ApplicationService, OrganisationService}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.application.AddApplicationsView

import scala.concurrent.ExecutionContext

class ApplicationControllerSpec extends HmrcSpec with GuiceOneAppPerSuite
    with ThirdPartyDeveloperConnectorMockModule
    with UserBuilder
    with LocalUserIdTracker
    with OrganisationIdFixtures
    with FixedClock {
  implicit val ec: ExecutionContext = ExecutionContext.global

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
  extends ThirdPartyDeveloperConnectorMockModule
      with LocalUserIdTracker {

    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val applicationService                   = app.injector.instanceOf[ApplicationService]
    val organisationService               = app.injector.instanceOf[OrganisationService]
    val addApplicationsView               = app.injector.instanceOf[AddApplicationsView]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val underTest =
      new ApplicationController(
        mcc,
        applicationService,
        organisationService,
        addApplicationsView,
        ThirdPartyDeveloperConnectorMock.aMock,
        errorHandler,
        cookieSigner
      )

    implicit val loggedInUser: User = user

  }

  "GET /organisation/:oid/add-applications" should {
    "return 200" in new Setup {
      ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
      val fakeRequest = CSRFTokenHelper.addCSRFToken(FakeRequest("GET", s"/organisation/${organisationIdOne.toString()}/add-applications").withUser(underTest)(sessionId))

      val result = underTest.addApplications(organisationIdOne)(fakeRequest)
      status(result) shouldBe Status.OK
    }
  }

}

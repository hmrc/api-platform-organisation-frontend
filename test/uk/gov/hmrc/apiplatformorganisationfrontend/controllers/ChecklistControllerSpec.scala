/*
 * Copyright 2025 HM Revenue & Customs
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

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithCSRFAddToken
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers._
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.{OrganisationActionServiceMockModule, SubmissionServiceMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.ChecklistView

class ChecklistControllerSpec
    extends HmrcSpec
    with GuiceOneAppPerSuite
    with SubmissionServiceMockModule
    with WithCSRFAddToken
    with LocalUserIdTracker {

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends SubmissionServiceMockModule
      with SubmissionsTestData
      with ThirdPartyDeveloperConnectorMockModule
      with OrganisationActionServiceMockModule
      with HasSessionDeveloperFlow
      with LocalUserIdTracker {

    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val productionCredentialsChecklistView = app.injector.instanceOf[ChecklistView]

    val controller = new ChecklistController(
      errorHandler,
      mcc,
      cookieSigner,
      SubmissionServiceMock.aMock,
      OrganisationActionServiceMock.aMock,
      productionCredentialsChecklistView,
      ThirdPartyDeveloperConnectorMock.aMock
    )

    val loggedInRequest             = FakeRequest().withUser(controller)(sessionId).withSession(sessionParams: _*)
    implicit val loggedInUser: User = user

    ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
  }

  "productionCredentialsChecklist" should {
    "fail with NOT FOUND" in new Setup {
      SubmissionServiceMock.Fetch.thenReturnsNone()

      val result = controller.checklistPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }

    "succeed with submission" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(partiallyAnsweredExtendedSubmission)

      val result = controller.checklistPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Get verified on the Developer Hub")
    }

    "succeed with completed submission" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withCompletedProgress())

      val result = controller.checklistPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      contentAsString(result) should include("Get verified on the Developer Hub")
    }

    "fail with NOT FOUND if logged in user doesn't match submission user" in new Setup {
      SubmissionServiceMock.Fetch.thenReturnsWrongUser(partiallyAnsweredExtendedSubmission)

      val result = controller.checklistPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }

  "productionCredentialsChecklistAction" should {
    "return success when form is valid and incomplete" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(partiallyAnsweredExtendedSubmission)
      val result = controller.checklistAction(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Complete the enter organisation details section")
      contentAsString(result) should include("Complete the enter responsible individual details section")
    }

    "redirect when when form is valid and complete" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(completelyAnswerExtendedSubmission)

      val result = controller.checklistAction(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(uk.gov.hmrc.apiplatformorganisationfrontend.controllers.routes.CheckAnswersController.checkAnswersPage(submissionId).url)
    }
  }
}

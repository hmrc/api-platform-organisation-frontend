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
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.QuestionnaireState.{Completed, InProgress}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{ExtendedSubmission, QuestionnaireProgress}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession._
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers._
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.views.html.CheckAnswersView
import uk.gov.hmrc.apiplatformorganisationfrontend.{AsIdsHelpers, WithCSRFAddToken}

class CheckAnswersControllerSpec
    extends HmrcSpec
    with GuiceOneAppPerSuite
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker
    with SubmissionsTestData {

  trait HasSessionDeveloperFlow {
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends SubmissionServiceMockModule
      with HasSessionDeveloperFlow
      with ThirdPartyDeveloperConnectorMockModule
      with SubmissionsTestData
      with FixedClock
      with AsIdsHelpers
      with LocalUserIdTracker {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val completedProgress           = List(OrganisationDetails.questionnaire, ResponsibleIndividualDetails.questionnaire)
      .map(q => q.id -> QuestionnaireProgress(Completed, q.questions.asIds())).toMap
    val completedExtendedSubmission = ExtendedSubmission(aSubmission, completedProgress)

    val incompleteProgress           = List(OrganisationDetails.questionnaire, ResponsibleIndividualDetails.questionnaire)
      .map(q => q.id -> QuestionnaireProgress(InProgress, q.questions.asIds())).toMap
    val incompleteExtendedSubmission = ExtendedSubmission(aSubmission, incompleteProgress)

    val checkAnswersView              = app.injector.instanceOf[CheckAnswersView]
    val mcc                           = app.injector.instanceOf[MessagesControllerComponents]
    val cookieSigner                  = app.injector.instanceOf[CookieSigner]
    val errorHandler                  = app.injector.instanceOf[ErrorHandler]
    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val controller = new CheckAnswersController(
      errorHandler,
      mcc,
      cookieSigner,
      SubmissionServiceMock.aMock,
      checkAnswersView,
      ThirdPartyDeveloperConnectorMock.aMock
    )

    val loggedInRequest = FakeRequest().withUser(controller)(sessionId).withSession(sessionParams: _*)

    ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
  }

  "checkAnswersPage" should {
    "succeed when submission is complete" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(answeredSubmission.withCompletedProgress())

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
    }

    "return an error when submission is not found" in new Setup {
      SubmissionServiceMock.Fetch.thenReturnsNone()

      val result = controller.checkAnswersPage(submissionId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe NOT_FOUND
    }
  }

  "checkAnswersAction" should {
    "fail as not implemented yet" in new Setup {
      SubmissionServiceMock.Fetch.thenReturns(answeringSubmission.withIncompleteProgress())

      val result = controller.checkAnswersAction(submissionId)(loggedInRequest.withCSRFToken)
      status(result) shouldBe BAD_REQUEST
    }
  }
}

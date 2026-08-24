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
import scala.concurrent.Future

import org.scalatest.AppendedClues
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.filters.csrf.CSRF
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.utils.{FixedClock, HmrcSpec}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WithCSRFAddToken
import uk.gov.hmrc.apiplatformorganisationfrontend.WithLoggedInSession.*
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.{ThirdPartyDeveloperConnectorMockModule, UpscanInitiateConnectorMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services.{OrganisationActionServiceMockModule, SubmissionServiceMockModule}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.upscan.services.UpscanInitiateResponse

class UploadControllerSpec
    extends HmrcSpec
    with GuiceOneAppPerSuite
    with WithCSRFAddToken
    with LocalUserIdTracker
    with SubmissionsTestData {

  trait HasSessionDeveloperFlow {
    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[CSRF.TokenProvider].generateToken)
  }

  override def fakeApplication(): Application = new GuiceApplicationBuilder().build()

  trait Setup
      extends SubmissionServiceMockModule
      with SubmissionsTestData
      with ThirdPartyDeveloperConnectorMockModule
      with OrganisationActionServiceMockModule
      with UpscanInitiateConnectorMockModule
      with HasSessionDeveloperFlow
      with AppendedClues
      with FixedClock
      with LocalUserIdTracker {

    implicit val hc: HeaderCarrier        = HeaderCarrier()
    val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]
    val cookieSigner: CookieSigner        = app.injector.instanceOf[CookieSigner]
    val errorHandler: ErrorHandler        = app.injector.instanceOf[ErrorHandler]

    implicit val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

    val controller = new UploadController(
      mcc,
      cookieSigner,
      errorHandler,
      OrganisationActionServiceMock.aMock,
      ThirdPartyDeveloperConnectorMock.aMock,
      UpscanInitiateConnectorMock.aMock,
      SubmissionServiceMock.aMock
    )(global, appConfig)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withUser(controller)(sessionId).withSession(sessionParams*)
    implicit val loggedInUser: User                          = user

    ThirdPartyDeveloperConnectorMock.FetchSession.succeeds()
  }

  "upscanResultRedirect" should {
    "succeed" in new Setup {
      val upscanResponse: UpscanInitiateResponse = upscanInitiateResponse(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      UpscanInitiateConnectorMock.Initiate.succeedsWith(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)(upscanResponse)
      SubmissionServiceMock.RecordAnswer.thenReturns(partiallyAnsweredExtendedSubmission)

      val attachmentRequest: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", s"${postTarget(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)}&key=$fileReference")
          .withUser(controller)(sessionId)
          .withSession(sessionParams*)
          .withCSRFToken

      val result: Future[Result] = controller.upscanResultRedirect()(attachmentRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe
        Some(s"/api-platform-organisation/submission/${aSubmission.id}/questionnaire/ac69b129-524a-4d10-89a5-7bfa46ed95c7/summary")
    }

    "fail with BadRequest" in new Setup {
      val upscanResponse: UpscanInitiateResponse = upscanInitiateResponse(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      UpscanInitiateConnectorMock.Initiate.succeedsWith(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)(upscanResponse)
      SubmissionServiceMock.RecordAnswer.thenReturns(partiallyAnsweredExtendedSubmission)

      val attachmentRequest: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", s"${postTarget(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)}")
          .withUser(controller)(sessionId)
          .withSession(sessionParams*)
          .withCSRFToken

      val result: Future[Result] = controller.upscanResultRedirect()(attachmentRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "fail with InternalServerError" in new Setup {
      val upscanResponse: UpscanInitiateResponse = upscanInitiateResponse(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)
      SubmissionServiceMock.Fetch.thenReturns(aSubmission.withIncompleteProgress())
      UpscanInitiateConnectorMock.Initiate.succeedsWith(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)(upscanResponse)
      SubmissionServiceMock.RecordAnswer.thenReturnsError("Failure")

      val attachmentRequest: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest("GET", s"${postTarget(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)}&key=$fileReference")
          .withUser(controller)(sessionId)
          .withSession(sessionParams*)
          .withCSRFToken

      val result: Future[Result] = controller.upscanResultRedirect()(attachmentRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}

/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformorganisationfrontend.connectors

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.{GetRegisteredOrUnregisteredUsersResponse, RegisteredOrUnregisteredUser}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WireMockExtensions
import uk.gov.hmrc.apiplatformorganisationfrontend.stubs.ThirdPartyDeveloperStub

class ThirdPartyDeveloperConnectorIntegrationSpec extends BaseConnectorIntegrationSpec
    with GuiceOneAppPerSuite with UserBuilder with LocalUserIdTracker with WireMockExtensions with FixedClock {

  private val stubConfig = Configuration(
    "microservice.services.third-party-developer.port" -> stubPort,
    "json.encryption.key"                              -> "czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY="
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val userEmail: LaxEmailAddress = "thirdpartydeveloper@example.com".toLaxEmail
    val userId: UserId             = idOf(userEmail)
    val sessionId: UserSessionId   = UserSessionId.random
    val userDetails                = RegisteredOrUnregisteredUser(userId, userEmail, true, true)

    val underTest: ThirdPartyDeveloperConnector = app.injector.instanceOf[ThirdPartyDeveloperConnector]
  }

  "fetchSession" should {
    "return a session when a session exists" in new Setup {
      ThirdPartyDeveloperStub.FetchSession.succeeds(sessionId, userId, userEmail, nowAsText)

      private val result = await(underTest.fetchSession(sessionId))

      result shouldBe Some(UserSession(sessionId, loggedInState = LoggedInState.LOGGED_IN, buildTrackedUser(userEmail)))
    }

    "not return a session when a session does not exist" in new Setup {
      ThirdPartyDeveloperStub.FetchSession.failsToFindSession(sessionId)

      val result = await(underTest.fetchSession(sessionId))

      result shouldBe None
    }

    "throw an UpstreamErrorResponse when the call returns an internal server error" in new Setup {
      ThirdPartyDeveloperStub.FetchSession.throwsAnException(sessionId)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchSession(sessionId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "getRegisteredOrUnregisteredUsers" should {
    "return a list of user details" in new Setup {
      ThirdPartyDeveloperStub.GetRegisteredOrUnregisteredUsers.succeeds(userId, userEmail)

      private val result = await(underTest.getRegisteredOrUnregisteredUsers(List(userId)))

      result shouldBe GetRegisteredOrUnregisteredUsersResponse(List(userDetails))
    }

    "throw an UpstreamErrorResponse when the call returns an internal server error" in new Setup {
      ThirdPartyDeveloperStub.GetRegisteredOrUnregisteredUsers.throwsAnException()

      intercept[UpstreamErrorResponse] {
        await(underTest.getRegisteredOrUnregisteredUsers(List(userId)))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}

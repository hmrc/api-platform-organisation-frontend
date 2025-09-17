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

package uk.gov.hmrc.apiplatformorganisationfrontend.services

import play.api.http.Status._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.GetAppsForAdminOrRIRequest
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ThirdPartyOrchestratorConnector
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

class ApplicationServiceSpec extends AsyncHmrcSpec with OrganisationIdFixtures
  with ApplicationIdFixtures with ApplicationWithCollaboratorsFixtures with ActorFixtures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  trait Setup extends FixedClock with LocalUserIdTracker {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockThirdPartyOrchestratorConnector = mock[ThirdPartyOrchestratorConnector]

    val underTest = new ApplicationService(
      mockThirdPartyOrchestratorConnector,
      clock
    )
  }

  "addOrgToApps" should {
    "return Future Unit" in new Setup {
      when(mockThirdPartyOrchestratorConnector.applicationCommandDispatch(*, *)(*)).thenReturn(successful(()))

      val result = await(underTest.addOrgToApps(collaboratorActorOne, organisationIdOne, Seq(applicationIdOne.toString, applicationIdTwo.toString)))

      result shouldBe ()
    }

    "throw error" in new Setup {
      when(mockThirdPartyOrchestratorConnector.applicationCommandDispatch(*, *)(*)).thenThrow(UpstreamErrorResponse("Error", INTERNAL_SERVER_ERROR))

      intercept[UpstreamErrorResponse] {
        await(underTest.addOrgToApps(collaboratorActorOne, organisationIdOne, Seq(applicationIdOne.toString, applicationIdTwo.toString)))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }


  "getAppsForResponsibleIndividualOrAdmin" should {
    val email             = LaxEmailAddress("a@example.com")
    val request = GetAppsForAdminOrRIRequest(adminOrRespIndEmail = email)
    val response = List(standardApp, standardApp2)

    "return applications on success when found" in new Setup {
      when(mockThirdPartyOrchestratorConnector.getAppsForResponsibleIndividualOrAdmin(*)(*)).thenReturn(successful(response))

      val result = await(underTest.getAppsForResponsibleIndividualOrAdmin(email))
      result shouldBe response

      verify(mockThirdPartyOrchestratorConnector).getAppsForResponsibleIndividualOrAdmin(eqTo(request))(*)
    }

    "return no applications when none found" in new Setup {
      when(mockThirdPartyOrchestratorConnector.getAppsForResponsibleIndividualOrAdmin(*)(*)).thenReturn(successful(List.empty))

      val result = await(underTest.getAppsForResponsibleIndividualOrAdmin(email))
      result shouldBe List.empty

      verify(mockThirdPartyOrchestratorConnector).getAppsForResponsibleIndividualOrAdmin(eqTo(request))(*)
    }

    "throw error" in new Setup {
      when(mockThirdPartyOrchestratorConnector.getAppsForResponsibleIndividualOrAdmin(*)(*)).thenThrow(UpstreamErrorResponse("Error", INTERNAL_SERVER_ERROR))

      intercept[UpstreamErrorResponse] {
        await(underTest.getAppsForResponsibleIndividualOrAdmin(email))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}

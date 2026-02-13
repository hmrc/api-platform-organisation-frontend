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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import play.api.mvc.{AnyContent, MessagesRequest}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationName}
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.RegisteredOrUnregisteredUser
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.AsyncHmrcSpec
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.OrganisationConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.UserRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import uk.gov.hmrc.apiplatformorganisationfrontend.models.OrganisationWithAllMembersDetails

class OrganisationActionServiceSpec extends AsyncHmrcSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  trait Setup extends FixedClock with ThirdPartyDeveloperConnectorMockModule with LocalUserIdTracker {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val orgId             = OrganisationId.random
    val userId            = userSession.developer.userId
    val email             = LaxEmailAddress("bob@example.com")
    val organisation      = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Member(userId)))
    val userDetails       = RegisteredOrUnregisteredUser(userId, email, true, true)
    val orgWithAllMembers = OrganisationWithAllMembersDetails(organisation, List(userDetails))

    val mockOrganisationConnector = mock[OrganisationConnector]

    val underTest = new OrganisationActionService(
      mockOrganisationConnector
    )

    val request     = mock[MessagesRequest[AnyContent]]
    val userRequest = new UserRequest(userSession, request)
  }

  "process" should {
    "return organisation request for given org id" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(Some(organisation)))

      val result = await(underTest.process(orgId, userRequest))

      result shouldBe defined
      result.get.organisation.id shouldBe orgId
    }

    "return None if not a member of organisation" in new Setup {
      val org = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Member(UserId.random)))
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(Some(org)))

      val result = await(underTest.process(orgId, userRequest))

      result shouldBe None
    }

    "return None if organisation not found" in new Setup {
      when(mockOrganisationConnector.fetchOrganisation(*[OrganisationId])(*)).thenReturn(successful(None))

      val result = await(underTest.process(orgId, userRequest))

      result shouldBe None
    }
  }
}

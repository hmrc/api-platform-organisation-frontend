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

package uk.gov.hmrc.apiplatformorganisationfrontend.mocks.services

import scala.concurrent.Future.successful

import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationId
import uk.gov.hmrc.apiplatformorganisationfrontend.models.OrganisationWithMembers
import uk.gov.hmrc.apiplatformorganisationfrontend.services.OrganisationService

trait OrganisationServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseOrganisationServiceMock {
    def aMock: OrganisationService

    object Fetch {

      def thenReturns(out: OrganisationWithMembers) = {
        when(aMock.fetch(*[OrganisationId])(*)).thenReturn(successful(Right(out)))
      }

      def thenReturnsNone() = {
        when(aMock.fetch(*[OrganisationId])(*)).thenReturn(successful(Left("Organisation not found")))
      }
    }
  }

  object OrganisationServiceMock extends BaseOrganisationServiceMock {
    val aMock = mock[OrganisationService](withSettings.strictness(Strictness.LENIENT))
  }
}

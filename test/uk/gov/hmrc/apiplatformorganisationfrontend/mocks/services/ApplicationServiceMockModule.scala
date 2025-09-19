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

import uk.gov.hmrc.http.InternalServerException

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, LaxEmailAddress, OrganisationId}
import uk.gov.hmrc.apiplatformorganisationfrontend.services.ApplicationService

trait ApplicationServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseApplicationServiceMock {
    def aMock: ApplicationService

    object GetAppsForResponsibleIndividualOrAdmin {

      def thenReturns(out: List[ApplicationWithCollaborators]) = {
        when(aMock.getAppsForResponsibleIndividualOrAdmin(*[LaxEmailAddress])(*)).thenReturn(successful(out))
      }

      def thenThrowsException() = {
        when(aMock.getAppsForResponsibleIndividualOrAdmin(*[LaxEmailAddress])(*)).thenThrow(new InternalServerException("Error"))
      }
    }

    object AddOrgToApps {

      def thenReturnsSuccess() = {
        when(aMock.addOrgToApps(*[Actors.AppCollaborator], *[OrganisationId], *)(*)).thenReturn(successful(()))
      }

      def thenThrowsException() = {
        when(aMock.addOrgToApps(*[Actors.AppCollaborator], *[OrganisationId], *)(*)).thenThrow(new InternalServerException("Error"))
      }
    }

  }

  object ApplicationServiceMock extends BaseApplicationServiceMock {
    val aMock = mock[ApplicationService](withSettings.strictness(Strictness.LENIENT))
  }
}

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

import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationId
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.Organisation
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatformorganisationfrontend.controllers.models.{OrganisationRequest, UserRequest}
import uk.gov.hmrc.apiplatformorganisationfrontend.services.OrganisationActionService

trait OrganisationActionServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseOrganisationActionServiceMock {
    def aMock: OrganisationActionService

    def givenOrganisationActionReturnsNotFound[A](organisationId: OrganisationId): Unit =
      when(aMock.process[A](eqTo(organisationId), *)(*))
        .thenReturn(successful(None))

    def givenOrganisationAction[A](
        org: Organisation,
        userSession: UserSession
      ): Unit = {

      def createReturn(req: UserRequest[A]): Future[Option[OrganisationRequest[A]]] = {
        org.members.find(_.userId == userSession.developer.userId) match {
          case None         => successful(None)
          case Some(member) => successful(Some(
              new OrganisationRequest(
                organisation = org,
                member = member,
                userRequest = req
              )
            ))
        }
      }
      reset(aMock)
      when(aMock.process[A](eqTo(org.id), *)(*))
        .thenAnswer((o: OrganisationId, request: UserRequest[A], c: HeaderCarrier) => createReturn(request))
    }

  }

  object OrganisationActionServiceMock extends BaseOrganisationActionServiceMock {
    val aMock = mock[OrganisationActionService](withSettings.strictness(Strictness.LENIENT))
  }
}

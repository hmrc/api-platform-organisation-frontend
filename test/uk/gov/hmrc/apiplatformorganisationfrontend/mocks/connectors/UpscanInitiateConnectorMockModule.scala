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

package uk.gov.hmrc.apiplatformorganisationfrontend.mocks.connectors

import scala.concurrent.Future.successful

import data.UpscanInitiateData
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{Question, SubmissionId}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.UpscanInitiateConnector
import uk.gov.hmrc.apiplatformorganisationfrontend.models.upscan.services.UpscanInitiateResponse

trait UpscanInitiateConnectorMockModule
    extends MockitoSugar
    with ArgumentMatchersSugar
    with UpscanInitiateData {

  trait AbstractUpscanInitiateConnectorMock {
    def aMock: UpscanInitiateConnector

    object Initiate {

      def succeedsWith(questionId: Question.Id, submissionId: SubmissionId, returnTo: Option[String] = None)(upscanInitiateResponse: UpscanInitiateResponse) = {
        when(aMock.initiate(eqTo(questionId), eqTo(submissionId), eqTo(returnTo))(any[HeaderCarrier])).thenReturn(successful(upscanInitiateResponse))
      }
    }
  }

  object UpscanInitiateConnectorMock extends AbstractUpscanInitiateConnectorMock {
    val aMock = mock[UpscanInitiateConnector]
  }
}

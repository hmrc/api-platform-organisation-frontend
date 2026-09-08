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

import play.api.http.Status.*
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application as PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatformorganisationfrontend.WireMockExtensions
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ApiPlatformDeskproConnector.*
import uk.gov.hmrc.apiplatformorganisationfrontend.stubs.ApiPlatformDeskproStub

class ApiPlatformDeskproConnectorIntegrationSpec
    extends BaseConnectorIntegrationSpec
    with GuiceOneAppPerSuite
    with WireMockExtensions {

  private val stubConfig = Configuration(
    "microservice.services.api-platform-deskpro.port" -> stubPort
  )

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest                  = app.injector.instanceOf[ApiPlatformDeskproConnector]

    val fullName      = "Bob"
    val email         = "bob@example.com"
    val subject       = "Test"
    val message       = "Message"
    val fileReference = "fileRef"
    val fileName      = "test-file.pdf"
    val deskproTicket = ApiPlatformDeskproConnector.CreateTicketRequest(fullName, email, subject, message)
    val status        = "awaiting_agent"
  }

  "createTicket" should {
    "return a ticket reference" in new Setup {
      val ticketReference = "DP12345"

      ApiPlatformDeskproStub.CreateTicket.succeeds(ticketReference)

      val result = await(underTest.createTicket(deskproTicket, hc))

      result shouldBe Some(ticketReference)
    }

    "return a ticket reference when creating ticket with attachments" in new Setup {
      val ticketReference       = "DP12346"
      val attachments           = List(Attachment(fileReference, fileName))
      val ticketWithAttachments = deskproTicket.copy(attachments = attachments)

      ApiPlatformDeskproStub.CreateTicket.succeedsWithAttachments(ticketReference, fullName, email, subject, message, attachments)

      val result = await(underTest.createTicket(ticketWithAttachments, hc))

      result shouldBe Some(ticketReference)
    }

    "fail when the ticket creation call returns an error" in new Setup {
      val failureStatus = INTERNAL_SERVER_ERROR
      ApiPlatformDeskproStub.CreateTicket.fails(failureStatus)

      intercept[UpstreamErrorResponse] {
        await(underTest.createTicket(deskproTicket, hc))
      }.statusCode shouldBe failureStatus
    }
  }
}

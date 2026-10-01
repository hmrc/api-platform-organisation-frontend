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

import java.time.Instant
import java.time.format.DateTimeFormatter

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status.*
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application as PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatformorganisationfrontend.WireMockExtensions
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ApiPlatformDeskproConnector.*
import uk.gov.hmrc.apiplatformorganisationfrontend.models.{DeskproAttachment, DeskproMessage, DeskproTicket}
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

    val fullName             = "Bob"
    val email                = LaxEmailAddress("bob@example.com")
    val subject              = "Test"
    val message              = "Message"
    val status               = "awaiting_agent"
    val ticketId             = 12345
    val ticketReference      = "DP12345"
    val fileReference        = "fileRef"
    val fileName             = "test-file.pdf"
    val attachment           = Attachment(fileReference, fileName)
    val attachments          = List(attachment)
    val createTicketRequest  = ApiPlatformDeskproConnector.CreateTicketRequest(fullName, email.text, subject, message)
    val createMessageRequest = ApiPlatformDeskproConnector.CreateMessageRequest(email, message, status, attachments)

  }

  "createTicket" should {
    "return a ticket reference" in new Setup {
      ApiPlatformDeskproStub.CreateTicket.succeeds(ticketReference, ticketId)

      val result = await(underTest.createTicket(createTicketRequest, hc))

      result shouldBe CreateTicketResponse(Some(ticketReference), Some(ticketId))
    }

    "return a ticket reference when creating ticket with attachments" in new Setup {
      val ticketWithAttachments = createTicketRequest.copy(attachments = attachments)

      ApiPlatformDeskproStub.CreateTicket.succeedsWithAttachments(ticketReference, ticketId, fullName, email.text, subject, message, attachments)

      val result = await(underTest.createTicket(ticketWithAttachments, hc))

      result shouldBe CreateTicketResponse(Some(ticketReference), Some(ticketId))
    }

    "fail when the ticket creation call returns an error" in new Setup {
      val failureStatus = INTERNAL_SERVER_ERROR
      ApiPlatformDeskproStub.CreateTicket.fails(failureStatus)

      intercept[UpstreamErrorResponse] {
        await(underTest.createTicket(createTicketRequest, hc))
      }.statusCode shouldBe failureStatus
    }
  }

  "createMessage" should {
    "return CreateMessageSuccess when creating message without attachments" in new Setup {
      ApiPlatformDeskproStub.CreateMessage.succeeds(ticketId, userEmail = email, message = message)

      val result = await(underTest.createMessage(ticketId, email, message, status, List.empty, hc))

      result shouldBe CreateMessageSuccess
    }

    "return CreateMessageSuccess when creating message with attachments" in new Setup {
      ApiPlatformDeskproStub.CreateMessage.succeedsWithFileAttachment(ticketId, userEmail = email, message = message, attachment)

      val result = await(underTest.createMessage(ticketId, email, message, status, attachments, hc))

      result shouldBe CreateMessageSuccess
    }

    "fail with CreateMessageNotFound when the message creation call returns NOT_FOUND" in new Setup {
      ApiPlatformDeskproStub.CreateMessage.notFound(ticketId)

      val result = await(underTest.createMessage(ticketId, email, message, status, attachments, hc))

      result shouldBe CreateMessageNotFound
    }

    "fail with CreateMessageFailure when the message creation call returns INTERNAL_SERVER_ERROR" in new Setup {
      ApiPlatformDeskproStub.CreateMessage.fails(ticketId)

      val result = await(underTest.createMessage(ticketId, email, message, status, attachments, hc))

      result shouldBe CreateMessageFailure
    }
  }

  "fetchTicket" should {
    "return a ticket" in new Setup {
      val ticketCreatedDate: Instant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2025-05-01T08:02:02Z"))
      val dateLastUpdated: Instant   = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2025-05-20T07:24:41Z"))
      val dateResolved: Instant      = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2025-05-23T09:27:46Z"))

      val message1CreatedDate: Instant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2025-05-01T08:02:02Z"))
      val message2CreatedDate: Instant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2025-05-19T11:54:53Z"))

      ApiPlatformDeskproStub.FetchTicket.succeeds(ticketId)

      val result = await(underTest.fetchTicket(ticketId, hc))

      val message1       = DeskproMessage(
        3467,
        ticketId,
        33,
        message1CreatedDate,
        false,
        "Hi. What API do I need to get next weeks lottery numbers?",
        List(DeskproAttachment("file.name", "https://example.com"))
      )
      val message2       = DeskproMessage(3698, ticketId, 61, message2CreatedDate, false, "Reply message from agent. What else gets filled in?", List.empty)
      val expectedTicket = DeskproTicket(
        ticketId,
        "SDST-2025XON927",
        61,
        LaxEmailAddress("bob@example.com"),
        "awaiting_user",
        ticketCreatedDate,
        dateLastUpdated,
        Some(dateResolved),
        "HMRC Developer Hub: Support Enquiry",
        List(message1, message2)
      )

      result shouldBe Some(expectedTicket)
    }

    "return None when not found" in new Setup {
      ApiPlatformDeskproStub.FetchTicket.fails(ticketId, NOT_FOUND)

      val result = await(underTest.fetchTicket(ticketId, hc))

      result shouldBe None
    }

    "fail when the ticket creation call returns an error" in new Setup {
      val failureStatus = INTERNAL_SERVER_ERROR

      ApiPlatformDeskproStub.FetchTicket.fails(ticketId, failureStatus)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchTicket(ticketId, hc))
      }.statusCode shouldBe failureStatus
    }
  }
}

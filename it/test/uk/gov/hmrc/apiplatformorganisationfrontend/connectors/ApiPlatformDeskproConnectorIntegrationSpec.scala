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

  "fetchTicket" should {
    val ticketId: Int = 3432
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

  "closeTicket" should {
    val ticketId: Int = 3432
    "close a ticket" in new Setup {
      ApiPlatformDeskproStub.CloseTicket.succeeds(ticketId)

      val result = await(underTest.closeTicket(ticketId, hc))

      result shouldBe DeskproTicketCloseSuccess
    }

    "fail when the ticket close call returns  not found" in new Setup {
      ApiPlatformDeskproStub.CloseTicket.notFound(ticketId)

      val result = await(underTest.closeTicket(ticketId, hc))

      result shouldBe DeskproTicketCloseNotFound
    }

    "fail when the ticket close call returns an error" in new Setup {
      val failureStatus = INTERNAL_SERVER_ERROR

      ApiPlatformDeskproStub.CloseTicket.fails(ticketId, failureStatus)

      val result = await(underTest.closeTicket(ticketId, hc))

      result shouldBe DeskproTicketCloseFailure
    }
  }

  "createResponse" should {
    val ticketId: Int = 3432
    val userEmail     = LaxEmailAddress("bob@example.com")

    "create a ticket response" in new Setup {
      ApiPlatformDeskproStub.CreateResponse.succeeds(ticketId, userEmail, message)

      val result = await(underTest.createResponse(ticketId, userEmail, message, status, List.empty, hc))

      result shouldBe DeskproTicketResponseSuccess
    }

    "create a ticket response with attached file" in new Setup {
      ApiPlatformDeskproStub.CreateResponse.succeedsWithFileAttachment(ticketId, userEmail, message, Attachment(fileReference, fileName))

      val result = await(underTest.createResponse(ticketId, userEmail, message, status, List(Attachment(fileReference, fileName)), hc))

      result shouldBe DeskproTicketResponseSuccess
    }

    "fail when the ticket to respond to is not found" in new Setup {
      ApiPlatformDeskproStub.CreateResponse.notFound(ticketId)

      val result = await(underTest.createResponse(ticketId, userEmail, message, status, List(Attachment(fileReference, fileName)), hc))

      result shouldBe DeskproTicketResponseNotFound
    }

    "fail when the ticket respond call returns an error" in new Setup {
      ApiPlatformDeskproStub.CreateResponse.fails(ticketId)

      val result = await(underTest.createResponse(ticketId, userEmail, message, status, List(Attachment(fileReference, fileName)), hc))

      result shouldBe DeskproTicketResponseFailure
    }

    "create a ticket response with multiple file attachments" in new Setup {
      ApiPlatformDeskproStub.CreateResponse.succeedsWithMultipleFileAttachments(
        ticketId,
        userEmail,
        message,
        List(Attachment("fileRef1", "file1.pdf"), Attachment("fileRef2", "file2.pdf"))
      )

      val result = await(underTest.createResponse(ticketId, userEmail, message, status, List(Attachment("fileRef1", "file1.pdf"), Attachment("fileRef2", "file2.pdf")), hc))

      result shouldBe DeskproTicketResponseSuccess
    }
  }

  "getTicketsForUser" should {
    val userEmail = LaxEmailAddress("bob@example.com")

    "return a ticket" in new Setup {
      val ticket1CreatedDate: Instant     = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2025-05-01T08:02:02Z"))
      val ticket1DateLastUpdated: Instant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2025-05-20T07:24:41Z"))
      val ticket2CreatedDate: Instant     = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2024-09-17T08:11:10Z"))
      val ticket2DateLastUpdated: Instant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2024-11-20T07:27:46Z"))

      ApiPlatformDeskproStub.GetTicketsForUser.succeeds(userEmail)

      val result = await(underTest.getTicketsForUser(userEmail, None, hc))

      val expectedTicket1 = DeskproTicket(
        3432,
        "SDST-2025XON927",
        61,
        LaxEmailAddress("bob@example.com"),
        "awaiting_user",
        ticket1CreatedDate,
        ticket1DateLastUpdated,
        None,
        "HMRC Developer Hub: Support Enquiry",
        List.empty
      )
      val expectedTicket2 = DeskproTicket(
        1041,
        "SDST-2024LTN085",
        61,
        LaxEmailAddress("bob@example.com"),
        "awaiting_agent",
        ticket2CreatedDate,
        ticket2DateLastUpdated,
        None,
        "HMRC Developer Hub: Support Enquiry",
        List.empty
      )

      result shouldBe List(expectedTicket1, expectedTicket2)
    }

    "fail when the ticket creation call returns an error" in new Setup {
      val failureStatus = INTERNAL_SERVER_ERROR

      ApiPlatformDeskproStub.GetTicketsForUser.fails(userEmail, failureStatus)

      intercept[UpstreamErrorResponse] {
        await(underTest.getTicketsForUser(userEmail, None, hc))
      }.statusCode shouldBe failureStatus
    }
  }
}

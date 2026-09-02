/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatformorganisationfrontend.stubs

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.stubbing.StubMapping

import play.api.http.Status.*
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.ApiPlatformDeskproConnector.Attachment

object ApiPlatformDeskproStub {

  object CreateTicket {

    def succeeds(ticketReference: String): StubMapping = {
      stubFor(
        post(urlEqualTo("/ticket"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.parse(s"""{"ref":"$ticketReference"}""").toString)
              .withHeader("content-type", "application/json")
          )
      )
    }

    def succeedsWithAttachments(ticketReference: String, fullName: String, email: String, subject: String, message: String, attachments: List[Attachment]): StubMapping = {
      val attachmentsJson = attachments.map(att => s"""{"fileReference": "${att.fileReference}", "fileName": "${att.fileName}"}""").mkString("[", ",", "]")
      stubFor(
        post(urlEqualTo("/ticket"))
          .withRequestBody(equalToJson(s"""{
                                          |  "fullName": "$fullName",
                                          |  "email": "$email",
                                          |  "subject": "$subject",
                                          |  "message": "$message",
                                          |  "attachments": $attachmentsJson
                                          |}""".stripMargin))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.parse(s"""{"ref":"$ticketReference"}""").toString)
              .withHeader("content-type", "application/json")
          )
      )
    }

    def fails(status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo("/ticket"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object FetchTicket {

    def succeeds(ticketId: Int): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/ticket/$ticketId"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("content-type", "application/json")
              .withBody("""{
                          |  "id": 3432,
                          |  "ref": "SDST-2025XON927",
                          |  "person": 61,
                          |  "personEmail": "bob@example.com",
                          |  "status": "awaiting_user",
                          |  "dateCreated": "2025-05-01T08:02:02Z",
                          |  "dateLastUpdated": "2025-05-20T07:24:41Z",
                          |  "dateResolved": "2025-05-23T09:27:46Z",
                          |  "subject": "HMRC Developer Hub: Support Enquiry",
                          |  "messages": [
                          |    {
                          |      "id": 3467,
                          |      "ticketId": 3432,
                          |      "person": 33,
                          |      "dateCreated": "2025-05-01T08:02:02Z",
                          |      "isAgentNote": false,
                          |      "message": "Hi. What API do I need to get next weeks lottery numbers?",
                          |      "attachments": [{"filename":"file.name","url":"https://example.com"}]
                          |    },
                          |    {
                          |      "id": 3698,
                          |      "ticketId": 3432,
                          |      "person": 61,
                          |      "dateCreated": "2025-05-19T11:54:53Z",
                          |      "isAgentNote": false,
                          |      "message": "Reply message from agent. What else gets filled in?",
                          |      "attachments": []
                          |    }
                          |  ]
                          |}""".stripMargin)
          )
      )
    }

    def fails(ticketId: Int, status: Int): StubMapping = {
      stubFor(
        get(urlEqualTo(s"/ticket/$ticketId"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object CloseTicket {

    def succeeds(ticketId: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/close"))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
    }

    def notFound(ticketId: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/close"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
    }

    def fails(ticketId: Int, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/close"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }

  object CreateResponse {

    def succeeds(ticketId: Int, userEmail: LaxEmailAddress, message: String): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/response"))
          .withRequestBody(equalToJson(s"""{
                                          |  "userEmail": "$userEmail",
                                          |  "message": "$message",
                                          |  "status": "awaiting_agent",
                                          |  "attachments": []
                                          |}""".stripMargin))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
    }

    def succeedsWithFileAttachment(ticketId: Int, userEmail: LaxEmailAddress, message: String, attachment: Attachment): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/response"))
          .withRequestBody(equalToJson(s"""{
                                          |  "userEmail": "$userEmail",
                                          |  "message": "$message",
                                          |  "status": "awaiting_agent",
                                          |  "attachments": [{"fileReference": "${attachment.fileReference}", "fileName": "${attachment.fileName}"}]
                                          |}""".stripMargin))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
    }

    def succeedsWithMultipleFileAttachments(ticketId: Int, userEmail: LaxEmailAddress, message: String, attachments: List[Attachment]): StubMapping = {
      val attachmentsJson = attachments.map(att => s"""{"fileReference": "${att.fileReference}", "fileName": "${att.fileName}"}""").mkString("[", ",", "]")
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/response"))
          .withRequestBody(equalToJson(s"""{
                                          |  "userEmail": "$userEmail",
                                          |  "message": "$message",
                                          |  "status": "awaiting_agent",
                                          |  "attachments": $attachmentsJson
                                          |}""".stripMargin))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
    }

    def notFound(ticketId: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/response"))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )
    }

    def fails(ticketId: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/$ticketId/response"))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
    }
  }

  object GetTicketsForUser {

    def succeeds(emailAddress: LaxEmailAddress): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/query"))
          .withRequestBody(equalToJson(
            s"""
               |{
               |  "email":"${emailAddress.text}"
               |}
               |""".stripMargin
          ))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("content-type", "application/json")
              .withBody("""[
                          |  {
                          |    "id": 3432,
                          |    "ref": "SDST-2025XON927",
                          |    "person": 61,
                          |    "personEmail": "bob@example.com",
                          |    "status": "awaiting_user",
                          |    "dateCreated": "2025-05-01T08:02:02Z",
                          |    "dateLastUpdated": "2025-05-20T07:24:41Z",
                          |    "subject": "HMRC Developer Hub: Support Enquiry",
                          |    "messages": []
                          |  },
                          |  {
                          |    "id": 1041,
                          |    "ref": "SDST-2024LTN085",
                          |    "person": 61,
                          |    "personEmail": "bob@example.com",
                          |    "status": "awaiting_agent",
                          |    "dateCreated": "2024-09-17T08:11:10Z",
                          |    "dateLastUpdated": "2024-11-20T07:27:46Z",
                          |    "subject": "HMRC Developer Hub: Support Enquiry",
                          |    "messages": []
                          |  }
                          |]""".stripMargin)
          )
      )
    }

    def fails(emailAddress: LaxEmailAddress, status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/ticket/query"))
          .withRequestBody(equalToJson(
            s"""
               |{
               |  "email":"${emailAddress.text}"
               |}
               |""".stripMargin
          ))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }
}

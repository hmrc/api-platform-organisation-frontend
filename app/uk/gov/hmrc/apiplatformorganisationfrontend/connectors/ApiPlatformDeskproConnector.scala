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

package uk.gov.hmrc.apiplatformorganisationfrontend.connectors

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatformorganisationfrontend.models.DeskproTicket

object ApiPlatformDeskproConnector {

  case class Config(
      serviceBaseUrl: String,
      authToken: String
    )

  case class CreateTicketRequest(
      fullName: String,
      email: String,
      subject: String,
      message: String,
      apiName: Option[String] = None,
      applicationId: Option[String] = None,
      organisation: Option[String] = None,
      supportReason: Option[String] = None,
      reasonKey: Option[String] = None,
      teamMemberEmail: Option[String] = None,
      service: Option[String] = None,
      referrer: Option[String] = None,
      sessionId: Option[String] = None,
      userAgent: Option[String] = None,
      organisationSubmissionId: Option[String] = None,
      attachments: List[Attachment] = List.empty
    )

  case class CreateTicketResponse(ref: Option[String], id: Option[Int])

  case class Attachment(fileReference: String, fileName: String)

  case class CreateMessageRequest(userEmail: LaxEmailAddress, message: String, status: String, attachments: List[Attachment] = List.empty)

  sealed trait DeskproTicketCloseResult
  object DeskproTicketCloseSuccess  extends DeskproTicketCloseResult
  object DeskproTicketCloseNotFound extends DeskproTicketCloseResult
  object DeskproTicketCloseFailure  extends DeskproTicketCloseResult

  sealed trait CreateMessageResult
  object CreateMessageSuccess  extends CreateMessageResult
  object CreateMessageNotFound extends CreateMessageResult
  object CreateMessageFailure  extends CreateMessageResult

  case class GetTicketsByEmailRequest(email: LaxEmailAddress, status: Option[String] = None)

  implicit val attachmentFormat: Format[Attachment]                              = Json.format[Attachment]
  implicit val createTicketRequestFormat: Format[CreateTicketRequest]            = Json.format[CreateTicketRequest]
  implicit val createTicketResponseFormat: Format[CreateTicketResponse]          = Json.format[CreateTicketResponse]
  implicit val getTicketsByEmailRequest: Format[GetTicketsByEmailRequest]        = Json.format[GetTicketsByEmailRequest]
  implicit val createMessageRequest: OFormat[CreateMessageRequest] = Json.format[CreateMessageRequest]
}

@Singleton
class ApiPlatformDeskproConnector @Inject()(http: HttpClientV2, config: ApiPlatformDeskproConnector.Config, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends Logging {

  import ApiPlatformDeskproConnector.*
  import play.api.libs.ws.writeableOf_JsValue

  val api = API("api-platform-deskpro")

  def createTicket(createRequest: CreateTicketRequest, hc: HeaderCarrier): Future[CreateTicketResponse] = metrics.record(api) {
    implicit val headerCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authToken)))
    val createRequestJson                     = Json.toJson(createRequest)
    http.post(url"${config.serviceBaseUrl}/ticket")
      .withBody(createRequestJson)
      .execute[CreateTicketResponse]
  }

  def createMessage(ticketId: Int, userEmail: LaxEmailAddress, message: String, status: String, attachments: List[Attachment], hc: HeaderCarrier)
  : Future[CreateMessageResult] = metrics.record(api) {
    implicit val headerCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authToken)))
    val createMessageRequestJson = Json.toJson(CreateMessageRequest(userEmail, message, status, attachments))
    http.post(url"${config.serviceBaseUrl}/ticket/$ticketId/response")
      .withBody(createMessageRequestJson)
      .execute[HttpResponse]
      .map(response =>
        response.status match {
          case OK =>
            logger.info(s"Create message for ticket '$ticketId' success")
            CreateMessageSuccess
          case NOT_FOUND =>
            logger.warn(s"Create message for ticket '$ticketId' failed Not found")
            CreateMessageNotFound
          case _ =>
            logger.error(s"Create message for ticket '$ticketId' failed status: ${response.status}. Response body: ${response.body}")
            CreateMessageFailure
        }
      )
  }

  def fetchTicket(ticketId: Int, hc: HeaderCarrier): Future[Option[DeskproTicket]] = metrics.record(api) {
    implicit val headerCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authToken)))
    http.get(url"${config.serviceBaseUrl}/ticket/$ticketId")
      .execute[Option[DeskproTicket]]
  }
}

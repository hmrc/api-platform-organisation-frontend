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

import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatformorganisationfrontend.models.DeskproTicket
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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
      attachments: List[Attachment] = List.empty
    )

  case class CreateTicketResponse(ref: Option[String])

  case class Attachment(fileReference: String, fileName: String)

  case class CreateTicketResponseRequest(userEmail: LaxEmailAddress, message: String, status: String, attachments: List[Attachment] = List.empty)

  sealed trait DeskproTicketCloseResult
  object DeskproTicketCloseSuccess  extends DeskproTicketCloseResult
  object DeskproTicketCloseNotFound extends DeskproTicketCloseResult
  object DeskproTicketCloseFailure  extends DeskproTicketCloseResult

  sealed trait DeskproTicketResponseResult
  object DeskproTicketResponseSuccess  extends DeskproTicketResponseResult
  object DeskproTicketResponseNotFound extends DeskproTicketResponseResult
  object DeskproTicketResponseFailure  extends DeskproTicketResponseResult

  case class GetTicketsByEmailRequest(email: LaxEmailAddress, status: Option[String] = None)

  implicit val attachmentFormat: Format[Attachment]                              = Json.format[Attachment]
  implicit val createTicketRequestFormat: Format[CreateTicketRequest]            = Json.format[CreateTicketRequest]
  implicit val createTicketResponseFormat: Format[CreateTicketResponse]          = Json.format[CreateTicketResponse]
  implicit val getTicketsByEmailRequest: Format[GetTicketsByEmailRequest]        = Json.format[GetTicketsByEmailRequest]
  implicit val createTicketResponseRequest: OFormat[CreateTicketResponseRequest] = Json.format[CreateTicketResponseRequest]
}

@Singleton
class ApiPlatformDeskproConnector @Inject() (http: HttpClientV2, config: ApiPlatformDeskproConnector.Config, metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends Logging {

  import ApiPlatformDeskproConnector.*
  import play.api.libs.ws.writeableOf_JsValue

  val api = API("api-platform-deskpro")

  def createTicket(createRequest: CreateTicketRequest, hc: HeaderCarrier): Future[Option[String]] = metrics.record(api) {
    implicit val headerCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authToken)))
    val createRequestJson                     = Json.toJson(createRequest)
    http.post(url"${config.serviceBaseUrl}/ticket")
      .withBody(createRequestJson)
      .execute[CreateTicketResponse]
      .map(_.ref)
  }
}

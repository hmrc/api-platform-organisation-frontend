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
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatformorganisationfrontend.models.{DeskproTicket, UploadedFile}
import uk.gov.hmrc.apiplatformorganisationfrontend.models.upscan.services.UpscanFileReference
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
}

@Singleton
class ApiPlatformDeskproConnector @Inject() (http: HttpClientV2, config: ApiPlatformDeskproConnector.Config,
                                             metrics: ConnectorMetrics)(implicit val ec: ExecutionContext)
    extends Logging {

  import ApiPlatformDeskproConnector.*

  val api = API("api-platform-deskpro")

  def fetchFileForFileRef(fileReference: String, hc: HeaderCarrier): Future[Option[UploadedFile]] = metrics.record(api) {
    implicit val headerCarrier: HeaderCarrier = hc.copy(authorization = Some(Authorization(config.authToken)))
    println(s"In ApiPlatformDeskproConnector.fetchFileForFileRef. fileReference:$fileReference")
    http.get(url"${config.serviceBaseUrl}/upscan/file/$fileReference")
      .execute[Option[UploadedFile]]
  }
}

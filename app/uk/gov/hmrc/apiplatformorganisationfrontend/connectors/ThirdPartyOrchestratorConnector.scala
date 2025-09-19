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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logging
import play.api.http.Status.OK
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpReadsInstances.readFromJson
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{SessionId => _, StringContextOps, _}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.GetAppsForAdminOrRIRequest
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchRequest

@Singleton
class ThirdPartyOrchestratorConnector @Inject() (
    http: HttpClientV2,
    config: ThirdPartyOrchestratorConnector.Config
  )(implicit val ec: ExecutionContext
  ) extends Logging {

  def getAppsForResponsibleIndividualOrAdmin(request: GetAppsForAdminOrRIRequest)(implicit hc: HeaderCarrier): Future[List[ApplicationWithCollaborators]] =
    http.post(url"${config.serviceBaseUrl}/responsible-ind-or-admin/applications")
      .withBody(Json.toJson(request))
      .execute[List[ApplicationWithCollaborators]]

  def applicationCommandDispatch(applicationId: String, request: DispatchRequest)(implicit hc: HeaderCarrier): Future[Unit] =
    http.patch(url"${config.serviceBaseUrl}/applications/$applicationId")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map(response =>
        response.status match {
          case OK     => ()
          case status =>
            logger.error(s"Dispatch failed with status code: $status")
            throw new InternalServerException(s"Failed calling dispatch $status")
        }
      )
}

object ThirdPartyOrchestratorConnector {
  case class Config(serviceBaseUrl: String)
}

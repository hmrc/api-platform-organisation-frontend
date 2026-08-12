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

package uk.gov.hmrc.apiplatformorganisationfrontend.controllers

import play.api.libs.crypto.CookieSigner
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.apiplatformorganisationfrontend.config.{AppConfig, ErrorHandler}
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{ThirdPartyDeveloperConnector, UpscanInitiateConnector}
import uk.gov.hmrc.apiplatformorganisationfrontend.services.OrganisationActionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class UpscanController @Inject()(
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val errorHandler: ErrorHandler,
    val organisationActionService: OrganisationActionService,
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    upscanInitiateConnector: UpscanInitiateConnector
  )(implicit val ec: ExecutionContext,
    val appConfig: AppConfig
  ) extends BaseController(mcc) {

  // Returns fresh Upscan upload fields for multi-file uploads.
  def initiateUpscan(): Action[AnyContent] = loggedInAction { implicit request =>
    upscanInitiateConnector.initiate().map(upscanInitiateResponse =>
      Ok(Json.toJson(upscanInitiateResponse))
    )
  }

  def upscanResultRedirect: Action[AnyContent] = Action { _ =>
    overrideIframeHeaders(Ok("Successful upload"))
  }

  private def overrideIframeHeaders(result: Result) = {
    result.withHeaders(
      SecurityHeadersFilter.X_FRAME_OPTIONS_HEADER         -> "ALLOWALL",
      SecurityHeadersFilter.CONTENT_SECURITY_POLICY_HEADER -> "frame-ancestors *"
    )
  }
}

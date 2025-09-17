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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.Helpers.OK
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures}
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.GetAppsForAdminOrRIRequest
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchRequest
import uk.gov.hmrc.apiplatformorganisationfrontend.WireMockExtensions.withJsonRequestBodySyntax

object ThirdPartyOrchestratorStub extends ApplicationWithCollaboratorsFixtures {

  object GetAppsForResponsibleIndividualOrAdmin {

    def succeeds(request: GetAppsForAdminOrRIRequest)(app: ApplicationWithCollaborators): StubMapping = {
      stubFor(
        post(urlPathEqualTo(s"/responsible-ind-or-admin/applications"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(s"[${getBody(app)}]")
          )
      )
    }

    def throwsAnException(request: GetAppsForAdminOrRIRequest) = {
      stubFor(
        post(urlPathEqualTo(s"/responsible-ind-or-admin/applications"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
    }
  }

  object ApplicationCommandDispatch {

    def succeeds(applicationId: String, request: DispatchRequest): StubMapping = {
      stubFor(
        patch(urlPathEqualTo(s"/applications/$applicationId"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )
    }

    def throwsAnException(applicationId: String, request: DispatchRequest) = {
      stubFor(
        patch(urlPathEqualTo(s"/applications/$applicationId"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )
    }
  }

  def getBody(app: ApplicationWithCollaborators) = {
    Json.toJson(app).toString
  }
}

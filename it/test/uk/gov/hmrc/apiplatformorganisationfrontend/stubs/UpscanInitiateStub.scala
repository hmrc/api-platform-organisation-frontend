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

import play.api.http.Status.OK
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatformorganisationfrontend.WireMockExtensions.withJsonRequestBodySyntax
import uk.gov.hmrc.apiplatformorganisationfrontend.connectors.{PreparedUpload, UpscanInitiateRequest}

object UpscanInitiateStub {

  object Initiate {

    def succeeds(request: UpscanInitiateRequest, response: PreparedUpload): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/upscan/v2/initiate"))
          .withJsonRequestBody(request)
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(response).toString())
          )
      )
    }

    def fails(status: Int): StubMapping = {
      stubFor(
        post(urlEqualTo(s"/upscan/v2/initiate"))
          .willReturn(
            aResponse()
              .withStatus(status)
          )
      )
    }
  }
}

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

import data.UpscanInitiateData
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status.*
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Json, Writes}
import play.api.{Application as PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatformorganisationfrontend.models.ErrorMessage
import uk.gov.hmrc.apiplatformorganisationfrontend.models.upscan.services.{UpscanFileReference, UpscanInitiateResponse}
import uk.gov.hmrc.apiplatformorganisationfrontend.stubs.UpscanInitiateStub

class UpscanInitiateConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {

  private val stubConfig = Configuration(
    "microservice.services.upscan-initiate.port" -> stubPort,
    "upscan.callback-endpoint"                   -> "http://localhost:9614/upscan-callback",
    "internal.platform.host"                     -> "http://localhost:15503"
  )

  trait Setup extends SubmissionsTestData with UpscanInitiateData {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    given Writes[ErrorMessage]     = Json.writes[ErrorMessage]

    val underTest: UpscanInitiateConnector = app.injector.instanceOf[UpscanInitiateConnector]

    val redirectUrl =
      s"http://localhost:15503/api-platform-organisation/upscan/result?${queryParamsAsString(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id)}"

    val request = UpscanInitiateRequest(
      callbackUrl = "http://localhost:9614/upscan-callback",
      successRedirect = Some(redirectUrl),
      errorRedirect = Some(redirectUrl)
    )

    val preparedUploadResponse = PreparedUpload(
      reference = Reference(fileReference.reference),
      uploadRequest = UploadForm("/post-target", formFields)
    )
  }

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .in(Mode.Test)
      .build()

  "initiate" should {
    "success" in new Setup {
      UpscanInitiateStub.Initiate.succeeds(request, preparedUploadResponse)

      val result: UpscanInitiateResponse = await(underTest.initiate(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id))

      result.fileReference shouldBe fileReference
    }

    "failure" in new Setup {
      UpscanInitiateStub.Initiate.fails(INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.initiate(OrganisationDetails.questionNonUkWithoutAttachment.id, aSubmission.id))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}

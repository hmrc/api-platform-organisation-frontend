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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application => PlayApplication, Configuration, Mode}
import uk.gov.hmrc.apiplatformorganisationfrontend.models._
import uk.gov.hmrc.apiplatformorganisationfrontend.stubs.ApiPlatformOrganisationStub
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

class OrganisationConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {

  private val stubConfig = Configuration(
    "microservice.services.api-platform-organisation.port" -> stubPort
  )

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest                  = app.injector.instanceOf[OrganisationConnector]

    val organisationName = OrganisationName("Example")
    val request          = CreateOrganisationRequest(organisationName)
  }

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .in(Mode.Test)
      .build()

  "createOrganisation" should {
    "successfully create one" in new Setup {
      ApiPlatformOrganisationStub.CreateOrganisation.succeeds()

      val result = await(underTest.createOrganisation(request))

      result shouldBe Organisation("1234", organisationName)
    }

    "fail when the org creation call returns an error" in new Setup {
      ApiPlatformOrganisationStub.CreateOrganisation.fails(INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.createOrganisation(request))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

}

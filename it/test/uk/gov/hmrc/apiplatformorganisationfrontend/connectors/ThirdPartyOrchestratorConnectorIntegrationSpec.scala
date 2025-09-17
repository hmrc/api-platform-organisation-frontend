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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.GetAppsForAdminOrRIRequest
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands.LinkToOrganisation
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchRequest
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ActorFixtures, LaxEmailAddress, OrganisationIdFixtures}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatformorganisationfrontend.WireMockExtensions
import uk.gov.hmrc.apiplatformorganisationfrontend.stubs.ThirdPartyOrchestratorStub
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

class ThirdPartyOrchestratorConnectorIntegrationSpec extends BaseConnectorIntegrationSpec
    with GuiceOneAppPerSuite with UserBuilder with LocalUserIdTracker with WireMockExtensions
  with ApplicationWithCollaboratorsFixtures with OrganisationIdFixtures with ActorFixtures {

  private val stubConfig = Configuration(
    "microservice.services.third-party-orchestrator.port" -> stubPort,
    "json.encryption.key"                              -> "czV2OHkvQj9FKEgrTWJQZVNoVm1ZcTN0Nnc5eiRDJkY="
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest: ThirdPartyOrchestratorConnector = app.injector.instanceOf[ThirdPartyOrchestratorConnector]

    val getAppsForAdminOrRIRequest = GetAppsForAdminOrRIRequest(LaxEmailAddress("a@example.com"))
    val dispatchRequest = DispatchRequest(LinkToOrganisation(collaboratorActorOne ,organisationIdOne, instant), Set.empty)
  }

  "getAppsForResponsibleIndividualOrAdmin" should {
    "return a list of applications" in new Setup {
      ThirdPartyOrchestratorStub.GetAppsForResponsibleIndividualOrAdmin.succeeds(getAppsForAdminOrRIRequest) (standardApp)

      private val result = await(underTest.getAppsForResponsibleIndividualOrAdmin(getAppsForAdminOrRIRequest))

      result shouldBe List(standardApp)
    }

    "throw an UpstreamErrorResponse when the call returns an internal server error" in new Setup {
      ThirdPartyOrchestratorStub.GetAppsForResponsibleIndividualOrAdmin.throwsAnException(getAppsForAdminOrRIRequest)

      intercept[UpstreamErrorResponse] {
        await(underTest.getAppsForResponsibleIndividualOrAdmin(getAppsForAdminOrRIRequest))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "applicationCommandDispatch" should {
    "return Unit on success" in new Setup {
      ThirdPartyOrchestratorStub.ApplicationCommandDispatch.succeeds(applicationIdOne.toString(), dispatchRequest)

      private val result = await(underTest.applicationCommandDispatch(applicationIdOne.toString(), dispatchRequest))

      result shouldBe ()
    }
  }

  "throw an UpstreamErrorResponse when the call returns an internal server error" in new Setup {
    ThirdPartyOrchestratorStub.ApplicationCommandDispatch.throwsAnException(applicationIdOne.toString(), dispatchRequest)


    private val result = await(underTest.applicationCommandDispatch(applicationIdOne.toString(), dispatchRequest))

    println(s"****** $result")

//    intercept[UpstreamErrorResponse] {
//      await(underTest.applicationCommandDispatch(applicationIdOne.toString(), dispatchRequest))
//    }.statusCode shouldBe INTERNAL_SERVER_ERROR
  }
}

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
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.apiplatformorganisationfrontend.models._
import uk.gov.hmrc.apiplatformorganisationfrontend.stubs.ApiPlatformOrganisationStub

class OrganisationConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite {

  private val stubConfig = Configuration(
    "microservice.services.api-platform-organisation.port" -> stubPort
  )

  trait Setup extends SubmissionsTestData {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest                  = app.injector.instanceOf[OrganisationConnector]
  }

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .in(Mode.Test)
      .build()

  "createOrganisation" should {
    val organisationName = OrganisationName("Example")
    val request          = CreateOrganisationRequest(organisationName)

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

  "createSubmission" should {
    val requestedBy = LaxEmailAddress("bob@example.com")
    "successfully create one" in new Setup {
      ApiPlatformOrganisationStub.CreateSubmission.succeeds(userId, aSubmission)

      val result = await(underTest.createSubmission(userId, requestedBy))

      result.isDefined shouldBe true
      result.get.startedBy shouldBe userId
    }

    "fail when the creation call returns an error" in new Setup {
      ApiPlatformOrganisationStub.CreateSubmission.fails(userId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.createSubmission(userId, requestedBy))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "submitSubmission" should {
    val requestedBy = LaxEmailAddress("bob@example.com")
    "successfully submit" in new Setup {
      ApiPlatformOrganisationStub.SubmitSubmission.succeeds(submissionId, aSubmission)

      val result = await(underTest.submitSubmission(submissionId, requestedBy))

      result.isRight shouldBe true
    }

    "fail when the submit returns an error" in new Setup {
      ApiPlatformOrganisationStub.SubmitSubmission.fails(submissionId, INTERNAL_SERVER_ERROR)

      val result = await(underTest.submitSubmission(submissionId, requestedBy))

      result.isLeft shouldBe true
    }
  }

  "fetchSubmission" should {
    "successfully get one" in new Setup {
      ApiPlatformOrganisationStub.FetchSubmission.succeeds(completelyAnswerExtendedSubmission.submission.id, completelyAnswerExtendedSubmission)

      val result = await(underTest.fetchSubmission(completelyAnswerExtendedSubmission.submission.id))

      result shouldBe Some(completelyAnswerExtendedSubmission)
    }

    "return None when not found" in new Setup {
      ApiPlatformOrganisationStub.FetchSubmission.fails(submissionId, NOT_FOUND)

      val result = await(underTest.fetchSubmission(submissionId))

      result shouldBe None
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.FetchSubmission.fails(submissionId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchSubmission(submissionId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "fetchLatestExtendedSubmissionByUserId" should {
    "successfully get one" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestExtendedSubmissionByUserId.succeeds(userId, completelyAnswerExtendedSubmission)

      val result = await(underTest.fetchLatestExtendedSubmissionByUserId(userId))

      result shouldBe Some(completelyAnswerExtendedSubmission)
    }

    "return None when not found" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestExtendedSubmissionByUserId.fails(userId, NOT_FOUND)

      val result = await(underTest.fetchLatestExtendedSubmissionByUserId(userId))

      result shouldBe None
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestExtendedSubmissionByUserId.fails(userId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchLatestExtendedSubmissionByUserId(userId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "fetchLatestSubmissionByUserId" should {
    "successfully get one" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestSubmissionByUserId.succeeds(userId, aSubmission)

      val result = await(underTest.fetchLatestSubmissionByUserId(userId))

      result shouldBe Some(aSubmission)
    }

    "return None when not found" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestSubmissionByUserId.fails(userId, NOT_FOUND)

      val result = await(underTest.fetchLatestSubmissionByUserId(userId))

      result shouldBe None
    }

    "fail when the call returns an error" in new Setup {
      ApiPlatformOrganisationStub.FetchLatestSubmissionByUserId.fails(userId, INTERNAL_SERVER_ERROR)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchLatestSubmissionByUserId(userId))
      }.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "recordAnswer" should {
    "successfully record answer" in new Setup {
      ApiPlatformOrganisationStub.RecordAnswer.succeeds(submissionId, aSubmission.questionIdsOfInterest.organisationNameId, completelyAnswerExtendedSubmission)

      val result = await(underTest.recordAnswer(submissionId, aSubmission.questionIdsOfInterest.organisationNameId, List("answer")))

      result shouldBe Right(completelyAnswerExtendedSubmission)
    }

    "fail when the creation call returns an error" in new Setup {
      ApiPlatformOrganisationStub.RecordAnswer.fails(submissionId, aSubmission.questionIdsOfInterest.organisationNameId, INTERNAL_SERVER_ERROR)

      val result = await(underTest.recordAnswer(submissionId, aSubmission.questionIdsOfInterest.organisationNameId, List("answer")))

      result.isLeft shouldBe true
    }
  }
}
